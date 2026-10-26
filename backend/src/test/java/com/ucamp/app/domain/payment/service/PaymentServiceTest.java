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
        paymentService = new PaymentService(portOneClient, depositPaymentRepository, clock);
    }

    private DepositPayment paidPayment() {
        return DepositPayment.builder()
                .pgProvider("PORTONE")
                .pgTransactionKey("imp_123")
                .status(DepositPaymentStatus.PAID)
                .amount(20000)
                .build();
    }

    @Test
    @DisplayName("결제 취소 시 남은 금액 전액을 포트원에 취소 요청하고 REFUNDED 로 바꾼다")
    void cancel_paidPayment_refundsAll() {
        DepositPayment payment = paidPayment();
        given(depositPaymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));

        PaymentCancelResponse response = paymentService.cancel(1L, "테스트 취소");

        verify(portOneClient).cancelPayment("imp_123", 20000, "테스트 취소");
        assertThat(response.status()).isEqualTo(DepositPaymentStatus.REFUNDED);
        assertThat(response.refundedAmount()).isEqualTo(20000);
        assertThat(payment.getRefundedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("정책 환불액만큼 부분 환불하면 PART_REFUNDED 가 된다")
    void refund_partialAmount_partRefunded() {
        DepositPayment payment = paidPayment();
        given(depositPaymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));

        PaymentCancelResponse response = paymentService.refund(1L, 10000, "하루 전 취소");

        verify(portOneClient).cancelPayment("imp_123", 10000, "하루 전 취소");
        assertThat(response.status()).isEqualTo(DepositPaymentStatus.PART_REFUNDED);
    }

    @Test
    @DisplayName("없는 결제를 취소하면 PAYMENT_001")
    void cancel_notFound_throwsException() {
        given(depositPaymentRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.cancel(99L, "취소"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 환불된 결제는 포트원을 호출하지 않고 PAYMENT_003")
    void cancel_alreadyRefunded_doesNotCallPortOne() {
        DepositPayment payment = DepositPayment.builder()
                .pgProvider("PORTONE")
                .pgTransactionKey("imp_123")
                .status(DepositPaymentStatus.REFUNDED)
                .amount(20000)
                .refundedAmount(20000)
                .build();
        given(depositPaymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancel(1L, "취소"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_REFUNDABLE);
        verify(portOneClient, never()).cancelPayment(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("포트원 취소가 실패하면 결제 상태를 바꾸지 않는다")
    void cancel_portOneFails_keepsStatus() {
        DepositPayment payment = paidPayment();
        given(depositPaymentRepository.findByIdForUpdate(1L)).willReturn(Optional.of(payment));
        willThrow(new BusinessException(ErrorCode.PAYMENT_PORTONE_ERROR))
                .given(portOneClient).cancelPayment(anyString(), anyInt(), anyString());

        assertThatThrownBy(() -> paymentService.cancel(1L, "취소"))
                .isInstanceOf(BusinessException.class);
        assertThat(payment.getStatus()).isEqualTo(DepositPaymentStatus.PAID);
        assertThat(payment.getRefundedAmount()).isZero();
    }
}
