package com.ucamp.app.domain.cancel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.cancel.dto.request.CustomerCancelRequest;
import com.ucamp.app.domain.cancel.dto.request.OwnerCancelRequest;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.notification.NotificationFixture;
import com.ucamp.app.domain.payment.client.PortOneClient;

/**
 * 같은 예약에 취소가 동시에 여러 번 들어올 때 환불이 한 번만 나가는지 실제 DB 잠금으로 확인한다.
 * 취소는 접수 커밋 → 포트원 결제 취소 → 결과 반영 순서라, 접수에서 한 건만 통과하지 못하면 환불이 두 번 나간다.
 * 스레드마다 따로 트랜잭션을 열어야 잠금 경쟁이 일어나므로 @Transactional 을 붙이지 않고 끝나면 직접 지운다.
 * 포트원만 가짜로 두고, 결제 취소 호출 횟수와 금액으로 실제 환불 횟수를 센다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CancelConcurrencyTest {

    private static final long CATEGORY_ID = 9911L;
    private static final long SHOP_ID = 9911L;
    private static final long TREATMENT_ID = 9911L;
    private static final long USER_ID = 9911L;
    private static final long RESERVATION_ID = 9911L;
    private static final long PAYMENT_ID = 9911L;
    private static final int DEPOSIT = 20000;
    private static final String IMP_UID = "imp_9911";
    private static final AuthUser CUSTOMER = new AuthUser(USER_ID, UserRole.CUSTOMER, null);
    private static final AuthUser OWNER = new AuthUser(9912L, UserRole.OWNER, SHOP_ID);
    private static final OwnerCancelRequest SHOP_FAULT = new OwnerCancelRequest(CancelType.SHOP_FAULT, null, false,
            "원장님 개인 사정");

    @MockitoBean
    private PortOneClient portOneClient;

    @Autowired
    private CancelService cancelService;

    @Autowired
    private CancelQuoteService cancelQuoteService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbc, CATEGORY_ID, "취소 동시성 테스트");
        NotificationFixture.shop(jdbc, SHOP_ID, CATEGORY_ID, "11680", "ACTIVE");
        NotificationFixture.treatment(jdbc, TREATMENT_ID, SHOP_ID, "NORMAL");
        NotificationFixture.user(jdbc, USER_ID);
        insertConfirmedReservationWithPayment();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM NOTIFICATION_INBOX WHERE SHOP_ID = ?", SHOP_ID);
        jdbc.update("DELETE FROM GRACE_CANCEL_USAGE WHERE USER_ID = ?", USER_ID);
        jdbc.update("DELETE FROM RESERVATION_CANCEL WHERE RESERVATION_ID = ?", RESERVATION_ID);
        jdbc.update("DELETE FROM SHOP_SCHEDULE_EXCEPTION WHERE SHOP_ID = ?", SHOP_ID);
        jdbc.update("DELETE FROM DEPOSIT_PAYMENT WHERE RESERVATION_ID = ?", RESERVATION_ID);
        jdbc.update("DELETE FROM RESERVATION WHERE RESERVATION_ID = ?", RESERVATION_ID);
        jdbc.update("DELETE FROM TREATMENT WHERE TREATMENT_ID = ?", TREATMENT_ID);
        jdbc.update("DELETE FROM APP_USER WHERE USER_ID = ?", USER_ID);
        jdbc.update("DELETE FROM SHOP WHERE SHOP_ID = ?", SHOP_ID);
        jdbc.update("DELETE FROM CATEGORY WHERE CATEGORY_ID = ?", CATEGORY_ID);
    }

    @Test
    @DisplayName("고객이 취소 버튼을 동시에 10번 눌러도 취소는 한 건만 접수되고 환불은 한 번만 나간다")
    void customerCancelsTenTimesAtOnce_refundedOnce() throws Exception {
        int refundAmount = cancelQuoteService.getQuote(CUSTOMER, RESERVATION_ID).refundAmount();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> cancelService.cancelByCustomer(CUSTOMER, RESERVATION_ID,
                    new CustomerCancelRequest(refundAmount)));
        }

        List<Outcome> outcomes = runAtOnce(tasks);

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).extracting(Outcome::errorCode)
                .containsOnly(ErrorCode.CANCEL_ALREADY_REQUESTED);
        assertRefundedOnce(refundAmount);
    }

    @RepeatedTest(5)
    @DisplayName("고객 취소와 매장 사유 취소가 동시에 들어오면 한쪽만 접수되고 환불은 이긴 쪽 금액으로 한 번만 나간다")
    void customerAndOwnerCancelAtOnce_onlyOneRefunded() throws Exception {
        int customerRefund = cancelQuoteService.getQuote(CUSTOMER, RESERVATION_ID).refundAmount();

        List<Outcome> outcomes = runAtOnce(List.of(
                () -> cancelService.cancelByCustomer(CUSTOMER, RESERVATION_ID,
                        new CustomerCancelRequest(customerRefund)),
                () -> cancelService.cancelByOwner(OWNER, RESERVATION_ID, SHOP_FAULT)));
        Outcome customer = outcomes.get(0);
        Outcome owner = outcomes.get(1);

        // 둘 다 성공하면 환불이 두 번 나간 것이고, 둘 다 실패하면 취소가 사라진 것이다
        assertThat(customer.succeeded()).isNotEqualTo(owner.succeeded());
        Outcome lost = customer.succeeded() ? owner : customer;
        assertThat(lost.errorCode()).isEqualTo(ErrorCode.CANCEL_ALREADY_REQUESTED);
        // 매장 사유 취소는 예약금 전액, 고객 취소는 정책 금액을 환불한다
        assertRefundedOnce(customer.succeeded() ? customerRefund : DEPOSIT);
        String cancelType = jdbc.queryForObject("SELECT CANCEL_TYPE FROM RESERVATION_CANCEL WHERE RESERVATION_ID = ?",
                String.class, RESERVATION_ID);
        assertThat(cancelType).isEqualTo(customer.succeeded() ? "CUSTOMER" : "SHOP_FAULT");
    }

    // ---------------------------------------------------------------- 도우미

    /** 사흘 뒤 시술을 하루 전에 점유·결제해 확정한 예약. 시작 전이라 취소할 수 있다 */
    private void insertConfirmedReservationWithPayment() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startsAt = now.plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime heldAt = now.minusDays(1);
        LocalDateTime approvedAt = heldAt.plusMinutes(2);
        jdbc.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                    APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CONFIRMED_AT, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, ?, 'CONFIRMED', ?, ?, 40000, ?, ?, ?, ?, ?)
                """, RESERVATION_ID, USER_ID, TREATMENT_ID, Timestamp.valueOf(startsAt),
                Timestamp.valueOf(startsAt.plusHours(1)), DEPOSIT, Timestamp.valueOf(heldAt.plusMinutes(5)),
                Timestamp.valueOf(approvedAt), Timestamp.valueOf(heldAt), Timestamp.valueOf(heldAt));
        jdbc.update("""
                INSERT INTO DEPOSIT_PAYMENT (DEPOSIT_PAYMENT_ID, RESERVATION_ID, PG_PROVIDER, PG_TRANSACTION_KEY,
                    STATUS, AMOUNT, APPROVED_AT, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'PORTONE', ?, 'PAID', ?, ?, ?, ?)
                """, PAYMENT_ID, RESERVATION_ID, IMP_UID, DEPOSIT, Timestamp.valueOf(approvedAt),
                Timestamp.valueOf(approvedAt), Timestamp.valueOf(approvedAt));
    }

    /** 포트원 결제 취소는 한 번, 그 금액만큼만 환불 기록이 남고 취소 접수도 한 건이다 */
    private void assertRefundedOnce(int refundAmount) {
        verify(portOneClient, times(1)).cancelPayment(anyString(), anyInt(), anyString());
        verify(portOneClient).cancelPayment(eq(IMP_UID), eq(refundAmount), anyString());
        assertThat(jdbc.queryForObject("SELECT REFUNDED_AMOUNT FROM DEPOSIT_PAYMENT WHERE DEPOSIT_PAYMENT_ID = ?",
                Integer.class, PAYMENT_ID)).isEqualTo(refundAmount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM RESERVATION_CANCEL WHERE RESERVATION_ID = ?",
                Integer.class, RESERVATION_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT STATUS FROM RESERVATION WHERE RESERVATION_ID = ?", String.class,
                RESERVATION_ID)).isEqualTo("CANCELLED");
    }

    private record Outcome(Object value, ErrorCode errorCode) {

        boolean succeeded() {
            return errorCode == null;
        }
    }

    /** 작업들을 최대한 같은 순간에 시작한다. 업무 오류는 결과로 모으고, 그 밖의 오류는 그대로 던진다 */
    private static List<Outcome> runAtOnce(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return new Outcome(task.call(), null);
                    } catch (BusinessException e) {
                        return new Outcome(null, e.getErrorCode());
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
