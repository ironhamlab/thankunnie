package com.ucamp.app.domain.cancel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.cancel.dto.response.CancelQuoteResponse;
import com.ucamp.app.domain.cancel.policy.GraceCancelResult;
import com.ucamp.app.domain.cancel.repository.CancelTarget;
import com.ucamp.app.domain.cancel.repository.CancelTargetRepository;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

class CancelQuoteServiceTest {

    /** 현재 2026-11-01 23:59:30 (KST) */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T14:59:30Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final AuthUser CUSTOMER = new AuthUser(1L, UserRole.CUSTOMER, null);
    private static final AuthUser OTHER_CUSTOMER = new AuthUser(2L, UserRole.CUSTOMER, null);
    private static final AuthUser OWNER = new AuthUser(11L, UserRole.OWNER, 1L);

    private CancelTargetRepository cancelTargetRepository;
    private GraceCancelUsageService graceCancelUsageService;
    private CancelQuoteService cancelQuoteService;

    @BeforeEach
    void setUp() {
        cancelTargetRepository = mock(CancelTargetRepository.class);
        graceCancelUsageService = mock(GraceCancelUsageService.class);
        cancelQuoteService = new CancelQuoteService(cancelTargetRepository, graceCancelUsageService, CLOCK);
    }

    @Test
    @DisplayName("하루 전(자정 직전) 취소 견적은 50% 이며, 결제 후 10분이 지나 무료 취소는 적용되지 않는다")
    void getQuote_dayBefore_halfRefund() {
        givenTarget(confirmed(NOW.minusDays(2), NOW.minusDays(2).plusMinutes(2)));

        CancelQuoteResponse response = cancelQuoteService.getQuote(CUSTOMER, 100L);

        assertThat(response.reservationId()).isEqualTo(100L);
        assertThat(response.paidAmount()).isEqualTo(20_000);
        assertThat(response.refundRate()).isEqualTo(50);
        assertThat(response.policyRefundAmount()).isEqualTo(10_000);
        assertThat(response.refundAmount()).isEqualTo(10_000);
        assertThat(response.graceCancelResult()).isEqualTo(GraceCancelResult.WINDOW_PASSED);
        assertThat(response.graceApplied()).isFalse();
        assertThat(response.remainingGraceCount()).isEqualTo(1);
        assertThat(response.quotedAt()).isEqualTo(NOW.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
    }

    @Test
    @DisplayName("결제 후 10분 이내이고 오늘 미사용이면 무료 취소로 전액 환불 견적을 낸다")
    void getQuote_withinGraceWindow_fullRefund() {
        givenTarget(confirmed(NOW.minusMinutes(5), NOW.minusMinutes(3)));

        CancelQuoteResponse response = cancelQuoteService.getQuote(CUSTOMER, 100L);

        assertThat(response.graceCancelResult()).isEqualTo(GraceCancelResult.APPLIED);
        assertThat(response.refundAmount()).isEqualTo(20_000);
        assertThat(response.policyRefundAmount()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("무료 취소 사용 여부는 예약한 고객의 오늘(한국 날짜) 기록으로 판단한다")
    void getQuote_checksGraceUsageOfCustomerToday() {
        givenTarget(confirmed(NOW.minusMinutes(5), NOW.minusMinutes(3)));
        given(graceCancelUsageService.isUsed(1L, LocalDate.of(2026, 11, 1))).willReturn(true);

        CancelQuoteResponse response = cancelQuoteService.getQuote(OWNER, 100L);

        assertThat(response.graceCancelResult()).isEqualTo(GraceCancelResult.ALREADY_USED_TODAY);
        assertThat(response.remainingGraceCount()).isZero();
        verify(graceCancelUsageService).isUsed(1L, LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("없는 예약과 남의 예약은 찾을 수 없음으로 응답한다")
    void getQuote_missingOrOthers_notFound() {
        given(cancelTargetRepository.findByReservationId(anyLong())).willReturn(Optional.empty());
        assertErrorCode(() -> cancelQuoteService.getQuote(CUSTOMER, 999L), ErrorCode.CANCEL_RESERVATION_NOT_FOUND);

        givenTarget(confirmed(NOW.minusDays(2), NOW.minusDays(2).plusMinutes(2)));
        assertErrorCode(() -> cancelQuoteService.getQuote(OTHER_CUSTOMER, 100L),
                ErrorCode.CANCEL_RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("확정 전 예약과 시작된 예약은 견적을 내지 않는다")
    void getQuote_notCancellable() {
        givenTarget(new CancelTarget(100L, 1L, 1L, ReservationStatus.HOLD, STARTS_AT, NOW.minusMinutes(1),
                null, null));
        assertErrorCode(() -> cancelQuoteService.getQuote(CUSTOMER, 100L), ErrorCode.CANCEL_NOT_CONFIRMED);

        givenTarget(new CancelTarget(100L, 1L, 1L, ReservationStatus.CONFIRMED, NOW, NOW.minusDays(1),
                20_000, NOW.minusDays(1).plusMinutes(2)));
        assertErrorCode(() -> cancelQuoteService.getQuote(CUSTOMER, 100L), ErrorCode.CANCEL_ALREADY_STARTED);
    }

    @Test
    @DisplayName("확정 예약에 결제 완료 정보가 없으면 결제 정보 없음으로 응답한다")
    void getQuote_confirmedWithoutPayment_paymentNotFound() {
        givenTarget(new CancelTarget(100L, 1L, 1L, ReservationStatus.CONFIRMED, STARTS_AT, NOW.minusDays(2),
                null, null));

        assertErrorCode(() -> cancelQuoteService.getQuote(CUSTOMER, 100L), ErrorCode.PAYMENT_NOT_FOUND);
    }

    private CancelTarget confirmed(LocalDateTime heldAt, LocalDateTime approvedAt) {
        return new CancelTarget(100L, 1L, 1L, ReservationStatus.CONFIRMED, STARTS_AT, heldAt, 20_000, approvedAt);
    }

    private void givenTarget(CancelTarget target) {
        given(cancelTargetRepository.findByReservationId(any())).willReturn(Optional.of(target));
    }

    private void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
