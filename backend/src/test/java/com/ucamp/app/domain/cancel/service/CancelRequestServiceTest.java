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
import com.ucamp.app.domain.cancel.dto.request.OwnerCancelRequest;
import com.ucamp.app.domain.cancel.entity.CancelStatus;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.cancel.entity.ReservationCancel;
import com.ucamp.app.domain.cancel.repository.ReservationCancelRepository;
import com.ucamp.app.domain.schedule.repository.ShopScheduleExceptionRepository;

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
    private static final AuthUser OTHER_OWNER = new AuthUser(12L, UserRole.OWNER, 2L);

    @Autowired
    private CancelRequestService cancelRequestService;

    @Autowired
    private GraceCancelUsageService graceCancelUsageService;

    @Autowired
    private ReservationCancelRepository reservationCancelRepository;

    @Autowired
    private ShopScheduleExceptionRepository shopScheduleExceptionRepository;

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

    @Test
    @DisplayName("매장 사유 취소는 납부액 전액을 환불하고 무료 취소 횟수를 차감하지 않으며, 닫기를 고르면 그 시간을 닫는다")
    void acceptOwner_shopFaultWithClose_fullRefundAndClosed() {
        givenPaidReservation(NOW.minusMinutes(5));

        AcceptedCancel accepted = cancelRequestService.acceptOwnerCancel(OWNER, 100L,
                new OwnerCancelRequest(CancelType.SHOP_FAULT, null, true, "원장님 부상"), NOW);

        assertThat(accepted.refundAmount()).isEqualTo(20_000);
        assertThat(accepted.closed()).isTrue();
        assertThat(accepted.customerName()).isEqualTo("고객");
        assertThat(reservationStatus()).isEqualTo("CANCEL_PENDING");
        ReservationCancel cancel = reservationCancelRepository.findById(accepted.cancelId()).orElseThrow();
        assertThat(cancel.getCancelType()).isEqualTo(CancelType.SHOP_FAULT);
        assertThat(cancel.getUserId()).isEqualTo(CancelFixture.CUSTOMER_ID);
        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, NOW.toLocalDate())).isFalse();
        assertThat(shopScheduleExceptionRepository.existsByReservationId(100L)).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM SHOP_SCHEDULE_EXCEPTION
                WHERE RESERVATION_ID = 100 AND SHOP_ID = 1 AND EXCEPTION_TYPE = 'CLOSE'
                  AND STARTS_AT = TIMESTAMP '2026-11-02 10:00:00' AND ENDS_AT = TIMESTAMP '2026-11-02 11:00:00'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("열기를 고르면 닫기 일정을 만들지 않는다")
    void acceptOwner_open_noCloseSchedule() {
        givenPaidReservation(LocalDateTime.of(2026, 10, 30, 12, 0));

        AcceptedCancel accepted = cancelRequestService.acceptOwnerCancel(OWNER, 100L,
                new OwnerCancelRequest(CancelType.SHOP_FAULT, null, false, null), NOW);

        assertThat(accepted.closed()).isFalse();
        assertThat(shopScheduleExceptionRepository.existsByReservationId(100L)).isFalse();
    }

    @Test
    @DisplayName("고객 요청 대리 취소는 정책 환불액~납부액 범위의 입력 금액을 환불한다")
    void acceptOwner_customerRequest_inRange() {
        givenPaidReservation(LocalDateTime.of(2026, 10, 30, 12, 0));

        AcceptedCancel accepted = cancelRequestService.acceptOwnerCancel(OWNER, 100L,
                new OwnerCancelRequest(CancelType.CUSTOMER_REQUEST, 15_000, false, "고객 전화 요청"), NOW);

        assertThat(accepted.refundAmount()).isEqualTo(15_000);
        ReservationCancel cancel = reservationCancelRepository.findById(accepted.cancelId()).orElseThrow();
        assertThat(cancel.getPolicyRefundAmount()).isEqualTo(10_000);
        assertThat(cancel.getRefundAmount()).isEqualTo(15_000);
    }

    @Test
    @DisplayName("대리 취소 환불액이 범위를 벗어나거나 없으면 거부하고 예약은 그대로 둔다")
    void acceptOwner_customerRequest_invalidAmount() {
        givenPaidReservation(LocalDateTime.of(2026, 10, 30, 12, 0));

        assertErrorCode(() -> cancelRequestService.acceptOwnerCancel(OWNER, 100L,
                new OwnerCancelRequest(CancelType.CUSTOMER_REQUEST, 9_900, false, null), NOW),
                ErrorCode.CANCEL_INVALID_REFUND_AMOUNT);
        assertErrorCode(() -> cancelRequestService.acceptOwnerCancel(OWNER, 100L,
                new OwnerCancelRequest(CancelType.CUSTOMER_REQUEST, null, false, null), NOW),
                ErrorCode.INVALID_INPUT);
        assertThat(reservationStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("다른 매장 사장님과 고객은 사장님 취소를 할 수 없다")
    void acceptOwner_otherOwnerOrCustomer_rejected() {
        givenPaidReservation(LocalDateTime.of(2026, 10, 30, 12, 0));

        assertErrorCode(() -> cancelRequestService.acceptOwnerCancel(OTHER_OWNER, 100L,
                new OwnerCancelRequest(CancelType.SHOP_FAULT, null, false, null), NOW),
                ErrorCode.CANCEL_RESERVATION_NOT_FOUND);
        assertErrorCode(() -> cancelRequestService.acceptOwnerCancel(CUSTOMER, 100L,
                new OwnerCancelRequest(CancelType.SHOP_FAULT, null, false, null), NOW),
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
