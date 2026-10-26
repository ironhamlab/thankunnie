package com.ucamp.app.domain.cancel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.cancel.dto.request.CustomerCancelRequest;
import com.ucamp.app.domain.cancel.dto.response.CancelResultResponse;
import com.ucamp.app.domain.cancel.entity.CancelStatus;
import com.ucamp.app.domain.payment.service.PaymentService;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

class CancelServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final AuthUser CUSTOMER = new AuthUser(1L, UserRole.CUSTOMER, null);
    private static final CancelResultResponse COMPLETED = result(CancelStatus.COMPLETED, ReservationStatus.CANCELLED);
    private static final CancelResultResponse UNKNOWN = result(CancelStatus.UNKNOWN, ReservationStatus.CANCEL_PENDING);

    private CancelRequestService cancelRequestService;
    private CancelResultService cancelResultService;
    private PaymentService paymentService;
    private CancelService cancelService;

    @BeforeEach
    void setUp() {
        cancelRequestService = mock(CancelRequestService.class);
        cancelResultService = mock(CancelResultService.class);
        paymentService = mock(PaymentService.class);
        cancelService = new CancelService(cancelRequestService, cancelResultService, paymentService, CLOCK);
        given(cancelResultService.complete(anyLong(), any())).willReturn(COMPLETED);
        given(cancelResultService.markUnknown(anyLong())).willReturn(UNKNOWN);
    }

    @Test
    @DisplayName("환불액이 0원이면 결제 취소를 호출하지 않고 바로 완료한다")
    void cancel_zeroRefund_skipsPayment() {
        givenAccepted(0);

        CancelResultResponse response = cancelService.cancelByCustomer(CUSTOMER, 100L, new CustomerCancelRequest(0));

        assertThat(response).isSameAs(COMPLETED);
        verify(paymentService, never()).refund(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("결제 취소에 성공하면 결제 ID·환불액으로 환불을 호출하고 완료를 반영한다")
    void cancel_refundSuccess_completes() {
        givenAccepted(10_000);

        CancelResultResponse response = cancelService.cancelByCustomer(CUSTOMER, 100L,
                new CustomerCancelRequest(10_000));

        assertThat(response).isSameAs(COMPLETED);
        verify(paymentService).refund(eq(5L), eq(10_000), anyString());
        verify(cancelResultService).complete(eq(7L), any());
    }

    @Test
    @DisplayName("결제 취소가 확실히 실패하면 예약을 확정으로 되돌리고 결제 에러를 그대로 응답한다")
    void cancel_refundRejected_failsAndRethrows() {
        givenAccepted(10_000);
        BusinessException rejected = new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR);
        given(paymentService.refund(anyLong(), anyInt(), anyString())).willThrow(rejected);

        assertThatThrownBy(() -> cancelService.cancelByCustomer(CUSTOMER, 100L, new CustomerCancelRequest(10_000)))
                .isSameAs(rejected);
        verify(cancelResultService).fail(eq(7L), any());
        verify(cancelResultService, never()).complete(anyLong(), any());
    }

    @Test
    @DisplayName("결제 취소 결과가 불명이면 취소 처리 중으로 두고 확인 필요로 응답한다")
    void cancel_refundUncertain_marksUnknown() {
        givenAccepted(10_000);
        given(paymentService.refund(anyLong(), anyInt(), anyString()))
                .willThrow(new BusinessException(ErrorCode.PAYMENT_REFUND_UNCERTAIN));

        CancelResultResponse response = cancelService.cancelByCustomer(CUSTOMER, 100L,
                new CustomerCancelRequest(10_000));

        assertThat(response).isSameAs(UNKNOWN);
        verify(cancelResultService, never()).fail(anyLong(), any());
    }

    @Test
    @DisplayName("결제 취소 중 예상 못 한 오류도 환불됐을 수 있으므로 취소 처리 중으로 둔다")
    void cancel_unexpectedError_marksUnknown() {
        givenAccepted(10_000);
        given(paymentService.refund(anyLong(), anyInt(), anyString())).willThrow(new IllegalStateException("boom"));

        CancelResultResponse response = cancelService.cancelByCustomer(CUSTOMER, 100L,
                new CustomerCancelRequest(10_000));

        assertThat(response).isSameAs(UNKNOWN);
        verify(cancelResultService, never()).fail(anyLong(), any());
    }

    private void givenAccepted(int refundAmount) {
        given(cancelRequestService.acceptCustomerCancel(any(), eq(100L), anyInt(), any()))
                .willReturn(new AcceptedCancel(7L, 100L, 5L, refundAmount));
    }

    private static CancelResultResponse result(CancelStatus cancelStatus, ReservationStatus reservationStatus) {
        return new CancelResultResponse(7L, 100L, null, cancelStatus, reservationStatus, 0, false, null, null);
    }
}
