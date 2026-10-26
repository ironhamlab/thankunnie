package com.ucamp.app.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
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

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.payment.client.PortOneClient;
import com.ucamp.app.domain.payment.dto.response.PaymentCancelResponse;
import com.ucamp.app.domain.payment.entity.DepositPayment;
import com.ucamp.app.domain.payment.entity.DepositPaymentStatus;
import com.ucamp.app.domain.payment.repository.DepositPaymentRepository;

class PaymentServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 14, 0);

    private PortOneClient portOneClient;
    private DepositPaymentRepository depositPaymentRepository;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        portOneClient = mock(PortOneClient.class);
        depositPaymentRepository = mock(DepositPaymentRepository.class);
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        PaymentRefundStateService refundStateService = new PaymentRefundStateService(depositPaymentRepository);
        paymentService = new PaymentService(portOneClient, refundStateService, clock);
    }

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
}
