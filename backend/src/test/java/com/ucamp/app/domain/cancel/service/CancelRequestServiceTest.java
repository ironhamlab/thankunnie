package com.ucamp.app.domain.cancel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.cancel.CancelFixture;
import com.ucamp.app.domain.cancel.entity.CancelStatus;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.cancel.entity.ReservationCancel;
import com.ucamp.app.domain.cancel.repository.ReservationCancelRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TimeConfig.class, CancelRequestService.class, CancelQuoteService.class, GraceCancelUsageService.class})
@ActiveProfiles("test")
class CancelRequestServiceTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);
    /** 시작 하루 전 (기본 정책 50%) */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0);
    private static final AuthUser CUSTOMER = new AuthUser(CancelFixture.CUSTOMER_ID, UserRole.CUSTOMER, null);
    private static final AuthUser OWNER = new AuthUser(11L, UserRole.OWNER, CancelFixture.SHOP_ID);

    @Autowired
    private CancelRequestService cancelRequestService;

    @Autowired
    private GraceCancelUsageService graceCancelUsageService;

    @Autowired
    private ReservationCancelRepository reservationCancelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        CancelFixture.insertBase(jdbcTemplate);
    }

    @Test
    @DisplayName("접수하면 예약은 취소 처리 중, 취소는 REQUESTED 로 저장되고 환불에 쓸 결제 ID 를 돌려준다")
    void accept_dayBefore_pendingAndRequested() {
        givenPaidReservation(LocalDateTime.of(2026, 10, 30, 12, 0));

        AcceptedCancel accepted = cancelRequestService.acceptCustomerCancel(CUSTOMER, 100L, 10_000, NOW);

        assertThat(accepted.reservationId()).isEqualTo(100L);
        assertThat(accepted.depositPaymentId()).isEqualTo(1L);
        assertThat(accepted.refundAmount()).isEqualTo(10_000);
        assertThat(reservationStatus()).isEqualTo("CANCEL_PENDING");
        ReservationCancel cancel = reservationCancelRepository.findById(accepted.cancelId()).orElseThrow();
        assertThat(cancel.getStatus()).isEqualTo(CancelStatus.REQUESTED);
        assertThat(cancel.getCancelType()).isEqualTo(CancelType.CUSTOMER);
        assertThat(cancel.getUserId()).isEqualTo(CancelFixture.CUSTOMER_ID);
        assertThat(cancel.getPolicyRefundAmount()).isEqualTo(10_000);
        assertThat(cancel.getRefundAmount()).isEqualTo(10_000);
        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, NOW.toLocalDate())).isFalse();
    }

    @Test
    @DisplayName("무료 취소가 적용되면 전액 환불로 접수하고 오늘 무료 취소 사용을 기록한다")
    void accept_grace_recordsUsage() {
        givenPaidReservation(NOW.minusMinutes(5));

        AcceptedCancel accepted = cancelRequestService.acceptCustomerCancel(CUSTOMER, 100L, 20_000, NOW);

        assertThat(accepted.refundAmount()).isEqualTo(20_000);
        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, LocalDate.of(2026, 11, 1))).isTrue();
    }

    @Test
    @DisplayName("재계산한 환불액이 화면 견적과 다르면 재확인을 요구하고 예약은 그대로 둔다")
    void accept_quoteChanged_requiresReconfirm() {
        givenPaidReservation(LocalDateTime.of(2026, 10, 30, 12, 0));

        assertErrorCode(() -> cancelRequestService.acceptCustomerCancel(CUSTOMER, 100L, 20_000, NOW),
                ErrorCode.CANCEL_QUOTE_CHANGED);
        assertThat(reservationStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("이미 접수된 예약을 다시 취소하면 이미 취소 요청됨으로 응답한다")
    void accept_twice_alreadyRequested() {
        givenPaidReservation(LocalDateTime.of(2026, 10, 30, 12, 0));
        cancelRequestService.acceptCustomerCancel(CUSTOMER, 100L, 10_000, NOW);

        assertErrorCode(() -> cancelRequestService.acceptCustomerCancel(CUSTOMER, 100L, 10_000, NOW),
                ErrorCode.CANCEL_ALREADY_REQUESTED);
    }

    @Test
    @DisplayName("결제 취소에 실패했던 예약은 같은 취소 행으로 다시 접수한다")
    void accept_afterFailure_retriesSameRow() {
        givenPaidReservation(LocalDateTime.of(2026, 10, 30, 12, 0));
        ReservationCancel failed = reservationCancelRepository.saveAndFlush(ReservationCancel.builder()
                .reservationId(100L)
                .userId(CancelFixture.CUSTOMER_ID)
                .cancelType(CancelType.CUSTOMER)
                .policyRefundAmount(20_000)
                .refundAmount(20_000)
                .requestedAt(NOW.minusDays(1))
                .build());
        failed.fail();

        AcceptedCancel accepted = cancelRequestService.acceptCustomerCancel(CUSTOMER, 100L, 10_000, NOW);

        assertThat(accepted.cancelId()).isEqualTo(failed.getId());
        assertThat(failed.getStatus()).isEqualTo(CancelStatus.REQUESTED);
        assertThat(failed.getRefundAmount()).isEqualTo(10_000);
        assertThat(failed.getRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("사장님은 고객 직접 취소를 할 수 없다")
    void accept_owner_forbidden() {
        givenPaidReservation(LocalDateTime.of(2026, 10, 30, 12, 0));

        assertErrorCode(() -> cancelRequestService.acceptCustomerCancel(OWNER, 100L, 10_000, NOW),
                ErrorCode.FORBIDDEN);
    }

    private void givenPaidReservation(LocalDateTime heldAt) {
        CancelFixture.insertReservation(jdbcTemplate, 100L, CancelFixture.CUSTOMER_ID, "CONFIRMED", STARTS_AT, heldAt);
        CancelFixture.insertPayment(jdbcTemplate, 1L, 100L, "PAID", 20_000, heldAt.plusMinutes(2));
    }

    private String reservationStatus() {
        return jdbcTemplate.queryForObject("SELECT STATUS FROM RESERVATION WHERE RESERVATION_ID = 100", String.class);
    }

    private void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
