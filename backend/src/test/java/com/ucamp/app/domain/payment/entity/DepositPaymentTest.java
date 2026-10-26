package com.ucamp.app.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

class DepositPaymentTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 14, 0);

    private DepositPayment payment(DepositPaymentStatus status, int amount, int refundedAmount) {
        return DepositPayment.builder()
                .pgProvider("PORTONE")
                .pgTransactionKey("imp_test")
                .status(status)
                .amount(amount)
                .refundedAmount(refundedAmount)
                .build();
    }

    @Test
    @DisplayName("환불을 시작하면 REFUND_PENDING 이 된다")
    void startRefund_paid_becomesRefundPending() {
        DepositPayment payment = payment(DepositPaymentStatus.PAID, 20000, 0);

        payment.startRefund(20000);

        assertThat(payment.getStatus()).isEqualTo(DepositPaymentStatus.REFUND_PENDING);
        assertThat(payment.getRefundedAmount()).isZero();
    }

    @Test
    @DisplayName("환불 처리 중인 결제는 다시 환불을 시작할 수 없다")
    void startRefund_refundPending_throwsException() {
        DepositPayment payment = payment(DepositPaymentStatus.REFUND_PENDING, 20000, 0);

        assertThatThrownBy(() -> payment.startRefund(20000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_REFUNDABLE);
    }

    @Test
    @DisplayName("이미 환불된 결제는 환불을 시작할 수 없다")
    void startRefund_refunded_throwsException() {
        DepositPayment payment = payment(DepositPaymentStatus.REFUNDED, 20000, 20000);

        assertThatThrownBy(() -> payment.startRefund(1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_REFUNDABLE);
    }

    @Test
    @DisplayName("남은 금액보다 많이 환불할 수 없다")
    void startRefund_overRemaining_throwsException() {
        DepositPayment payment = payment(DepositPaymentStatus.PAID, 20000, 0);

        assertThatThrownBy(() -> payment.startRefund(20001))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_INVALID_REFUND_AMOUNT);
    }

    @Test
    @DisplayName("0원 이하는 환불할 수 없다")
    void startRefund_zeroAmount_throwsException() {
        DepositPayment payment = payment(DepositPaymentStatus.PAID, 20000, 0);

        assertThatThrownBy(() -> payment.startRefund(0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_INVALID_REFUND_AMOUNT);
    }

    @Test
    @DisplayName("남은 금액을 모두 환불 완료하면 REFUNDED 가 된다")
    void completeRefund_fullAmount_becomesRefunded() {
        DepositPayment payment = payment(DepositPaymentStatus.PAID, 20000, 0);
        payment.startRefund(20000);

        payment.completeRefund(20000, NOW);

        assertThat(payment.getStatus()).isEqualTo(DepositPaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(20000);
        assertThat(payment.getRefundedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("일부만 환불 완료하면 PART_REFUNDED 가 되고 남은 금액이 줄어든다")
    void completeRefund_partialAmount_becomesPartRefunded() {
        DepositPayment payment = payment(DepositPaymentStatus.PAID, 20000, 0);
        payment.startRefund(10000);

        payment.completeRefund(10000, NOW);

        assertThat(payment.getStatus()).isEqualTo(DepositPaymentStatus.PART_REFUNDED);
        assertThat(payment.refundableAmount()).isEqualTo(10000);
    }

    @Test
    @DisplayName("환불을 시작하지 않은 결제는 환불 완료할 수 없다")
    void completeRefund_notPending_throwsException() {
        DepositPayment payment = payment(DepositPaymentStatus.PAID, 20000, 0);

        assertThatThrownBy(() -> payment.completeRefund(20000, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_REFUNDABLE);
    }

    @Test
    @DisplayName("PG 거절 시 환불 시작 전 상태로 되돌린다")
    void restoreStatus_pending_restoresPrevious() {
        DepositPayment payment = payment(DepositPaymentStatus.PART_REFUNDED, 20000, 10000);
        payment.startRefund(10000);

        payment.restoreStatus(DepositPaymentStatus.PART_REFUNDED);

        assertThat(payment.getStatus()).isEqualTo(DepositPaymentStatus.PART_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(10000);
    }
}
