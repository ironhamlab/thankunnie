package com.ucamp.app.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.RepetitionInfo;
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
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.notification.NotificationFixture;
import com.ucamp.app.domain.payment.client.PortOneClient;
import com.ucamp.app.domain.payment.client.PortOnePayment;
import com.ucamp.app.domain.payment.dto.request.PaymentVerifyRequest;
import com.ucamp.app.domain.payment.dto.response.PaymentVerifyResponse;
import com.ucamp.app.domain.reservation.service.ReservationService;

/**
 * 결제 확정이 다른 요청과 동시에 들어올 때 돈과 예약 상태가 어긋나지 않는지 실제 DB 잠금으로 확인한다.
 * 확정이 지고 결제가 남거나(돈은 받았는데 예약 없음), 확정과 해제가 둘 다 반영되면 안 된다.
 * 스레드마다 따로 트랜잭션을 열어야 잠금 경쟁이 일어나므로 @Transactional 을 붙이지 않고 끝나면 직접 지운다.
 * 포트원만 가짜로 두고, 결제 조회는 항상 "결제 완료"로 답한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentConfirmConcurrencyTest {

    private static final long CATEGORY_ID = 9901L;
    private static final long SHOP_ID = 9901L;
    private static final long TREATMENT_ID = 9901L;
    private static final long USER_ID = 9901L;
    private static final long RESERVATION_ID = 9901L;
    private static final int DEPOSIT = 20000;
    private static final String IMP_UID = "imp_9901";
    private static final AuthUser CUSTOMER = new AuthUser(USER_ID, UserRole.CUSTOMER, null);

    @MockitoBean
    private PortOneClient portOneClient;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbc, CATEGORY_ID, "결제 동시성 테스트");
        NotificationFixture.shop(jdbc, SHOP_ID, CATEGORY_ID, "11680", "ACTIVE");
        NotificationFixture.treatment(jdbc, TREATMENT_ID, SHOP_ID, "NORMAL");
        NotificationFixture.user(jdbc, USER_ID);
        LocalDateTime now = LocalDateTime.now(clock);
        // 결제 마감(시작 15분 전)에 걸리지 않게 사흘 뒤 시술을 방금 점유했다
        insertHold(now.plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0), now.plusMinutes(5));
        given(portOneClient.getPayment(IMP_UID)).willReturn(new PortOnePayment(IMP_UID,
                PaymentService.merchantUidPrefix(RESERVATION_ID) + "1", (long) DEPOSIT, "paid", "card",
                now.atZone(TimeUtils.KST).toEpochSecond()));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM NOTIFICATION_INBOX WHERE SHOP_ID = ?", SHOP_ID);
        jdbc.update("DELETE FROM DEPOSIT_PAYMENT WHERE RESERVATION_ID = ?", RESERVATION_ID);
        jdbc.update("DELETE FROM RESERVATION WHERE RESERVATION_ID = ?", RESERVATION_ID);
        jdbc.update("DELETE FROM TREATMENT WHERE TREATMENT_ID = ?", TREATMENT_ID);
        jdbc.update("DELETE FROM APP_USER WHERE USER_ID = ?", USER_ID);
        jdbc.update("DELETE FROM SHOP WHERE SHOP_ID = ?", SHOP_ID);
        jdbc.update("DELETE FROM CATEGORY WHERE CATEGORY_ID = ?", CATEGORY_ID);
    }

    @Test
    @DisplayName("같은 결제 확인이 동시에 10번 들어와도(새로고침·중복 호출) 결제는 한 건만 저장되고 모두 그 결제를 받으며 취소하지 않는다")
    void sameImpUid_verifiedTenTimesAtOnce_savedOnceNeverCancelled() throws Exception {
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> paymentService.verify(CUSTOMER, new PaymentVerifyRequest(IMP_UID, RESERVATION_ID, null)));
        }

        List<Outcome> outcomes = runAtOnce(tasks);

        // 뒤늦게 들어온 요청은 "이미 확정"에 걸리지만, 같은 결제가 저장된 것을 보고 성공으로 돌려줘야 한다
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.errorCode()).isNull());
        Long savedId = jdbc.queryForObject(
                "SELECT DEPOSIT_PAYMENT_ID FROM DEPOSIT_PAYMENT WHERE RESERVATION_ID = ?", Long.class, RESERVATION_ID);
        assertThat(outcomes).extracting(outcome -> ((PaymentVerifyResponse) outcome.value()).depositPaymentId())
                .containsOnly(savedId);
        assertThat(statusOf()).isEqualTo("CONFIRMED");
        // 여기서 취소하면 정상 결제가 환불된다
        verify(portOneClient, never()).cancelPayment(anyString(), anyInt(), anyString());
    }

    @RepeatedTest(10)
    @DisplayName("결제 확인과 점유 해제(결제 화면 이탈)가 동시에 오면 둘 중 하나만 반영되고, 해제가 이기면 받은 결제를 한 번 취소한다")
    void verifyAndRelease_atOnce_exactlyOneWins(RepetitionInfo repetition) throws Exception {
        // 결제 확인은 포트원 조회를 먼저 거쳐 늘 늦게 도착하므로, 해제를 조금씩 늦춰 양쪽이 이기는 경우를 모두 지나게 한다
        long releaseDelayMillis = (repetition.getCurrentRepetition() - 1) * 3L;
        List<Outcome> outcomes = runAtOnce(List.of(
                () -> paymentService.verify(CUSTOMER, new PaymentVerifyRequest(IMP_UID, RESERVATION_ID, null)),
                () -> {
                    Thread.sleep(releaseDelayMillis);
                    reservationService.releaseHold(CUSTOMER, RESERVATION_ID);
                    return null;
                }));
        Outcome verified = outcomes.get(0);
        Outcome released = outcomes.get(1);

        if (verified.errorCode() == null) {
            // 확정이 먼저 잠금을 잡았다: 예약은 확정, 결제는 한 건 저장, 해제는 이미 확정이라 거절
            assertThat(released.errorCode()).isEqualTo(ErrorCode.RESERVATION_ALREADY_CONFIRMED);
            assertThat(statusOf()).isEqualTo("CONFIRMED");
            assertThat(paidCount()).isEqualTo(1);
            verify(portOneClient, never()).cancelPayment(anyString(), anyInt(), anyString());
        } else {
            // 해제가 먼저 잠금을 잡았다: 예약은 만료, 결제는 저장하지 않고 포트원 결제를 한 번만 취소
            assertThat(released.errorCode()).isNull();
            assertThat(verified.errorCode()).isEqualTo(ErrorCode.RESERVATION_HOLD_EXPIRED);
            assertThat(statusOf()).isEqualTo("EXPIRED");
            assertThat(paidCount()).isZero();
            verify(portOneClient, times(1)).cancelPayment(eq(IMP_UID), eq(DEPOSIT), anyString());
        }
    }

    @Test
    @DisplayName("해제가 먼저 끝난 뒤 온 결제의 자동 취소가 실패하면 UNKNOWN 한 줄만 남고, 같은 결제를 다시 확인해도 성공으로 보지 않는다")
    void releasedThenVerify_cancelFails_savesUnknownOnce() {
        willThrow(new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR))
                .given(portOneClient).cancelPayment(anyString(), anyInt(), anyString());
        reservationService.releaseHold(CUSTOMER, RESERVATION_ID);
        PaymentVerifyRequest request = new PaymentVerifyRequest(IMP_UID, RESERVATION_ID, null);

        for (int i = 0; i < 2; i++) {
            ErrorCode errorCode = null;
            try {
                paymentService.verify(CUSTOMER, request);
            } catch (BusinessException e) {
                errorCode = e.getErrorCode();
            }
            // 두 번째 확인에서 UNKNOWN 기록을 "이미 확정된 결제"로 보면 돈만 빠진 결제가 성공으로 나간다
            assertThat(errorCode).isEqualTo(ErrorCode.RESERVATION_HOLD_EXPIRED);
        }

        assertThat(statusOf()).isEqualTo("EXPIRED");
        assertThat(paidCount()).isZero();
        List<String> saved = jdbc.queryForList(
                "SELECT STATUS FROM DEPOSIT_PAYMENT WHERE RESERVATION_ID = ? AND PG_TRANSACTION_KEY = ?",
                String.class, RESERVATION_ID, IMP_UID);
        assertThat(saved).containsExactly("UNKNOWN");
        verify(portOneClient, times(2)).cancelPayment(eq(IMP_UID), eq(DEPOSIT), anyString());
    }

    // ---------------------------------------------------------------- 도우미

    private void insertHold(LocalDateTime startsAt, LocalDateTime holdExpiresAt) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
        jdbc.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                    APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, ?, 'HOLD', ?, ?, 40000, ?, ?, ?, ?)
                """, RESERVATION_ID, USER_ID, TREATMENT_ID, Timestamp.valueOf(startsAt),
                Timestamp.valueOf(startsAt.plusHours(1)), DEPOSIT, Timestamp.valueOf(holdExpiresAt), now, now);
    }

    private String statusOf() {
        return jdbc.queryForObject("SELECT STATUS FROM RESERVATION WHERE RESERVATION_ID = ?", String.class,
                RESERVATION_ID);
    }

    private int paidCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM DEPOSIT_PAYMENT WHERE RESERVATION_ID = ? AND STATUS = 'PAID'", Integer.class,
                RESERVATION_ID);
        return count == null ? 0 : count;
    }

    private record Outcome(Object value, ErrorCode errorCode) {
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
