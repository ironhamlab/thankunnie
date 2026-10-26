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
    @DisplayName("남은 금액을 모두 환불하면 REFUNDED 가 된다")
    void refund_fullAmount_becomesRefunded() {
        DepositPayment payment = payment(DepositPaymentStatus.PAID, 20000, 0);

        payment.refund(20000, NOW);

        assertThat(payment.getStatus()).isEqualTo(DepositPaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(20000);
        assertThat(payment.getRefundedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("일부만 환불하면 PART_REFUNDED 가 되고 남은 금액이 줄어든다")
    void refund_partialAmount_becomesPartRefunded() {
        DepositPayment payment = payment(DepositPaymentStatus.PAID, 20000, 0);

        payment.refund(10000, NOW);

        assertThat(payment.getStatus()).isEqualTo(DepositPaymentStatus.PART_REFUNDED);
        assertThat(payment.refundableAmount()).isEqualTo(10000);
    }

    @Test
    @DisplayName("부분 환불 후 남은 금액을 환불하면 REFUNDED 가 된다")
    void refund_remainingAfterPartial_becomesRefunded() {
        DepositPayment payment = payment(DepositPaymentStatus.PART_REFUNDED, 20000, 10000);

        payment.refund(10000, NOW);

        assertThat(payment.getStatus()).isEqualTo(DepositPaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(20000);
    }

    @Test
    @DisplayName("이미 환불된 결제는 환불할 수 없다")
    void refund_alreadyRefunded_throwsException() {
        DepositPayment payment = payment(DepositPaymentStatus.REFUNDED, 20000, 20000);

        assertThatThrownBy(() -> payment.refund(1, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_REFUNDABLE);
    }

    @Test
    @DisplayName("남은 금액보다 많이 환불할 수 없다")
    void refund_overRemaining_throwsException() {
        DepositPayment payment = payment(DepositPaymentStatus.PAID, 20000, 0);

        assertThatThrownBy(() -> payment.refund(20001, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_INVALID_REFUND_AMOUNT);
    }

    @Test
    @DisplayName("0원 이하는 환불할 수 없다")
    void refund_zeroAmount_throwsException() {
        DepositPayment payment = payment(DepositPaymentStatus.PAID, 20000, 0);

        assertThatThrownBy(() -> payment.refund(0, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_INVALID_REFUND_AMOUNT);
    }
}
