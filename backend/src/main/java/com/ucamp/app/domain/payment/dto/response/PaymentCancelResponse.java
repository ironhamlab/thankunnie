package com.ucamp.app.domain.payment.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.payment.entity.DepositPayment;
import com.ucamp.app.domain.payment.entity.DepositPaymentStatus;

/**
 * 결제 취소 결과.
 */
public record PaymentCancelResponse(
        Long depositPaymentId,
        DepositPaymentStatus status,
        Integer amount,
        Integer refundedAmount,
        OffsetDateTime refundedAt
) {

    public static PaymentCancelResponse from(DepositPayment payment) {
        return new PaymentCancelResponse(
                payment.getDepositPaymentId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getRefundedAmount(),
                TimeUtils.toOffset(payment.getRefundedAt())
        );
    }
}
