package com.ucamp.app.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.payment.client.PortOneClient;
import com.ucamp.app.domain.payment.client.PortOnePayment;
import com.ucamp.app.domain.payment.dto.request.PaymentStartRequest;
import com.ucamp.app.domain.payment.dto.request.PaymentVerifyRequest;
import com.ucamp.app.domain.reservation.service.ReservationService;
import com.ucamp.app.domain.payment.dto.response.PaymentVerifyResponse;
import com.ucamp.app.domain.payment.dto.response.PaymentCancelResponse;
import com.ucamp.app.domain.payment.entity.DepositPayment;
import com.ucamp.app.domain.payment.entity.DepositPaymentStatus;
import com.ucamp.app.domain.payment.repository.DepositPaymentRepository;

class PaymentServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 14, 0);

    private PortOneClient portOneClient;
    private DepositPaymentRepository depositPaymentRepository;
    private PaymentConfirmService confirmService;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        portOneClient = mock(PortOneClient.class);
        depositPaymentRepository = mock(DepositPaymentRepository.class);
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        PaymentRefundStateService refundStateService = new PaymentRefundStateService(depositPaymentRepository);
        confirmService = mock(PaymentConfirmService.class);
        startService = new PaymentStartService(mock(ReservationService.class), clock);
        paymentService = new PaymentService(portOneClient, refundStateService, confirmService, startService, clock);
    }

    private PaymentStartService startService;

    private DepositPayment paidPayment() {
        DepositPayment payment = DepositPayment.builder()
                .pgProvider("PORTONE")
                .pgTransactionKey("imp_123")
                .status(DepositPaymentStatus.PAID)
                .amount(20000)
                .build();
        given(depositPaymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        return payment;
    }

    @Test
    @DisplayName("남은 금액 전액을 환불하면 포트원에 취소 요청하고 REFUNDED 로 바꾼다")
    void refund_fullAmount_refunded() {
        DepositPayment payment = paidPayment();

        PaymentCancelResponse response = paymentService.refund(1L, 20000, "테스트 취소");

        verify(portOneClient).cancelPayment("imp_123", 20000, "테스트 취소");
        assertThat(response.status()).isEqualTo(DepositPaymentStatus.REFUNDED);
        assertThat(response.refundedAmount()).isEqualTo(20000);
        assertThat(payment.getRefundedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("정책 환불액만큼 부분 환불하면 PART_REFUNDED 가 된다")
    void refund_partialAmount_partRefunded() {
        paidPayment();

        PaymentCancelResponse response = paymentService.refund(1L, 10000, "하루 전 취소");

        verify(portOneClient).cancelPayment("imp_123", 10000, "하루 전 취소");
        assertThat(response.status()).isEqualTo(DepositPaymentStatus.PART_REFUNDED);
    }

    @Test
    @DisplayName("없는 결제를 환불하면 PAYMENT_001")
    void refund_notFound_throwsException() {
        given(depositPaymentRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refund(99L, 20000, "취소"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 환불된 결제는 포트원을 호출하지 않고 PAYMENT_003")
    void refund_alreadyRefunded_doesNotCallPortOne() {
        DepositPayment payment = DepositPayment.builder()
                .pgProvider("PORTONE")
                .pgTransactionKey("imp_123")
                .status(DepositPaymentStatus.REFUNDED)
                .amount(20000)
                .refundedAmount(20000)
                .build();
        given(depositPaymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refund(1L, 20000, "취소"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_REFUNDABLE);
        verify(portOneClient, never()).cancelPayment(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("포트원이 취소를 거절하면 원래 상태로 되돌린다")
    void refund_portOneRejects_restoresStatus() {
        DepositPayment payment = paidPayment();
        willThrow(new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR))
                .given(portOneClient).cancelPayment(anyString(), anyInt(), anyString());

        assertThatThrownBy(() -> paymentService.refund(1L, 20000, "취소"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_PORTONE_ERROR);
        assertThat(payment.getStatus()).isEqualTo(DepositPaymentStatus.PAID);
        assertThat(payment.getRefundedAmount()).isZero();
    }

    @Test
    @DisplayName("포트원 응답이 불확실하면 REFUND_PENDING 을 유지해 재요청을 막는다")
    void refund_portOneUncertain_keepsRefundPending() {
        DepositPayment payment = paidPayment();
        willThrow(new BusinessException(ErrorCode.PAYMENT_REFUND_UNCERTAIN))
                .given(portOneClient).cancelPayment(anyString(), anyInt(), anyString());

        assertThatThrownBy(() -> paymentService.refund(1L, 20000, "취소"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_REFUND_UNCERTAIN);
        assertThat(payment.getStatus()).isEqualTo(DepositPaymentStatus.REFUND_PENDING);

        assertThatThrownBy(() -> paymentService.refund(1L, 20000, "재시도"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_REFUNDABLE);
    }

    // ===== 결제 확인 → 예약 확정 =====

    private static final AuthUser CUSTOMER = new AuthUser(1L, UserRole.CUSTOMER, null);
    private static final long RESERVATION_ID = 10L;
    // 2026-10-29 14:58:20 (KST)
    private static final long PAID_AT = 1793253500L;

    private PortOnePayment portOnePayment(String status, String merchantUid) {
        PortOnePayment payment = new PortOnePayment("imp_123", merchantUid, 20000L, status, "card", PAID_AT);
        given(portOneClient.getPayment("imp_123")).willReturn(payment);
        return payment;
    }

    private PaymentVerifyRequest verifyRequest() {
        return new PaymentVerifyRequest("imp_123", RESERVATION_ID, "손톱은 라운드로 부탁드려요");
    }

    @Test
    @DisplayName("결제 완료이고 주문번호가 예약과 맞으면 결제 금액·요청사항으로 예약을 확정하고 결제 ID 를 돌려준다")
    void verify_paidAndMatched_confirmsReservation() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(1L, RESERVATION_ID, "imp_123", 20000, "손톱은 라운드로 부탁드려요",
                LocalDateTime.of(2026, 10, 29, 14, 58, 20))).willReturn(7L);

        PaymentVerifyResponse response = paymentService.verify(CUSTOMER, verifyRequest());

        assertThat(response.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(response.depositPaymentId()).isEqualTo(7L);
        assertThat(response.amount()).isEqualTo(20000L);
        verify(portOneClient, never()).cancelPayment(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("결제가 완료되지 않았으면 PAYMENT_006 이고 예약을 확정하지 않는다")
    void verify_notPaid_throws() {
        portOnePayment("ready", "reservation-10-1730000000000");

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_PAID);
        verify(confirmService, never()).confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("결제 확인이 실패로 끝나도 결제 진행 중 표시를 풀어 다른 탭이 바로 결제할 수 있다")
    void verify_failed_releasesPaymentInProgress() {
        startService.start(CUSTOMER, new PaymentStartRequest(RESERVATION_ID, "tab-1"));
        assertThatThrownBy(() -> startService.start(CUSTOMER, new PaymentStartRequest(RESERVATION_ID, "tab-2")))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
        portOnePayment("ready", "reservation-10-1730000000000");

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_PAID);

        assertThat(startService.start(CUSTOMER, new PaymentStartRequest(RESERVATION_ID, "tab-2")).merchantUid())
                .startsWith("reservation-10-");
    }

    @Test
    @DisplayName("주문번호가 다른 예약의 것이면 PAYMENT_007 이고 예약을 확정하지 않는다")
    void verify_otherReservationMerchantUid_throws() {
        portOnePayment("paid", "reservation-100-1730000000000");

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_RESERVATION_MISMATCH);
        verify(confirmService, never()).confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("점유가 만료돼 확정할 수 없으면 결제 전액을 자동 취소하고 만료 오류를 그대로 던진다")
    void verify_holdExpired_cancelsPayment() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_HOLD_EXPIRED));
        given(confirmService.findSaved("imp_123", RESERVATION_ID, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXPIRED);
        verify(portOneClient).cancelPayment(eq("imp_123"), eq(20000), anyString());
    }

    @Test
    @DisplayName("결제창을 연 뒤 시술이 삭제돼 확정할 수 없으면 결제 전액을 자동 취소한다")
    void verify_treatmentDeleted_cancelsPayment() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.TREATMENT_NOT_FOUND));
        given(confirmService.findSaved("imp_123", RESERVATION_ID, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TREATMENT_NOT_FOUND);
        verify(portOneClient).cancelPayment(eq("imp_123"), eq(20000), anyString());
    }

    @Test
    @DisplayName("자동 취소가 실패해도 고객에게는 확정 실패 사유를 알린다")
    void verify_cancelFails_stillThrowsConfirmError() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_DEPOSIT_MISMATCH));
        given(confirmService.findSaved("imp_123", RESERVATION_ID, 1L)).willReturn(Optional.empty());
        willThrow(new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR))
                .given(portOneClient).cancelPayment(anyString(), anyInt(), anyString());

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_DEPOSIT_MISMATCH);
    }

    @Test
    @DisplayName("자동 취소 중 응답을 읽지 못해도 원래 확정 실패 사유를 알린다")
    void verify_cancelResponseUnreadable_stillThrowsConfirmError() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_HOLD_EXPIRED));
        given(confirmService.findSaved("imp_123", RESERVATION_ID, 1L)).willReturn(Optional.empty());
        willThrow(new ResourceAccessException("응답 읽기 실패"))
                .given(portOneClient).cancelPayment(anyString(), anyInt(), anyString());

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXPIRED);
    }

    @Test
    @DisplayName("자동 취소에 성공하면 결제 기록을 남기지 않는다")
    void verify_cancelSucceeds_savesNothing() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_HOLD_EXPIRED));
        given(confirmService.findSaved("imp_123", RESERVATION_ID, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXPIRED);
        verify(confirmService, never()).saveCancelUnknown(anyLong(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("자동 취소를 포트원이 거절하면 운영자가 찾을 수 있게 결제를 UNKNOWN 으로 남긴다")
    void verify_cancelRejected_savesUnknown() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_HOLD_EXPIRED));
        given(confirmService.findSaved("imp_123", RESERVATION_ID, 1L)).willReturn(Optional.empty());
        willThrow(new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR))
                .given(portOneClient).cancelPayment(anyString(), anyInt(), anyString());

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXPIRED);
        verify(confirmService).saveCancelUnknown(eq(RESERVATION_ID), eq("imp_123"), eq(20000),
                eq(LocalDateTime.of(2026, 10, 29, 14, 58, 20)));
    }

    @Test
    @DisplayName("자동 취소 결과를 알 수 없어도 결제를 UNKNOWN 으로 남긴다")
    void verify_cancelUncertain_savesUnknown() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_HOLD_EXPIRED));
        given(confirmService.findSaved("imp_123", RESERVATION_ID, 1L)).willReturn(Optional.empty());
        willThrow(new ResourceAccessException("응답 읽기 실패"))
                .given(portOneClient).cancelPayment(anyString(), anyInt(), anyString());

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXPIRED);
        verify(confirmService).saveCancelUnknown(eq(RESERVATION_ID), eq("imp_123"), eq(20000), any());
    }

    @Test
    @DisplayName("UNKNOWN 기록 저장까지 실패해도 고객에게는 원래 확정 실패 사유를 알린다")
    void verify_saveUnknownFails_stillThrowsConfirmError() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_HOLD_EXPIRED));
        given(confirmService.findSaved("imp_123", RESERVATION_ID, 1L)).willReturn(Optional.empty());
        willThrow(new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR))
                .given(portOneClient).cancelPayment(anyString(), anyInt(), anyString());
        willThrow(new IllegalStateException("DB 오류"))
                .given(confirmService).saveCancelUnknown(anyLong(), anyString(), anyInt(), any());

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXPIRED);
    }

    @Test
    @DisplayName("없는 예약이거나 다른 고객의 예약이면 남의 결제를 취소하지 않고 RESERVATION_001 이다")
    void verify_reservationNotFound_keepsPayment() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        given(confirmService.findSaved("imp_123", RESERVATION_ID, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
        verify(portOneClient, never()).cancelPayment(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("같은 결제를 동시에 두 번 보내 다른 요청이 먼저 확정했으면 취소하지 않고 성공으로 돌려준다")
    void verify_confirmedByOtherRequest_returnsSaved() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_ALREADY_CONFIRMED));
        given(confirmService.findSaved("imp_123", RESERVATION_ID, 1L)).willReturn(Optional.of(7L));

        PaymentVerifyResponse response = paymentService.verify(CUSTOMER, verifyRequest());

        assertThat(response.depositPaymentId()).isEqualTo(7L);
        verify(portOneClient, never()).cancelPayment(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("요청사항이 너무 길어 확정하지 못하면 다시 확인할 수 있도록 결제를 취소하지 않는다")
    void verify_invalidCustomerRequest_keepsPayment() {
        portOnePayment("paid", "reservation-10-1730000000000");
        given(confirmService.confirm(anyLong(), anyLong(), anyString(), anyInt(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT));
        given(confirmService.findSaved("imp_123", RESERVATION_ID, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.verify(CUSTOMER, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(portOneClient, never()).cancelPayment(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("고객이 아니면 포트원을 조회하지 않고 FORBIDDEN 이다")
    void verify_notCustomer_forbidden() {
        AuthUser owner = new AuthUser(2L, UserRole.OWNER, 1L);

        assertThatThrownBy(() -> paymentService.verify(owner, verifyRequest()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(portOneClient, never()).getPayment(anyString());
    }
}
