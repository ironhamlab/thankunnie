package com.ucamp.app.domain.payment.dto.response;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.payment.client.PortOnePayment;

import java.time.Instant;
import java.time.OffsetDateTime;

public record PaymentVerifyResponse(
        Long reservationId,
        Long depositPaymentId,
        String impUid,
        String merchantUid,
        Long amount,
        String status,
        String payMethod,
        OffsetDateTime paidAt) {

    public static PaymentVerifyResponse of(PortOnePayment payment, Long reservationId, Long depositPaymentId) {
        return new PaymentVerifyResponse(
                reservationId,
                depositPaymentId,
                payment.impUid(),
                payment.merchantUid(),
                payment.amount(),
                payment.status(),
                payment.payMethod(),
                toDateTime(payment.paidAt()));
    }

    // 포트원은 결제 시각을 Unix 초로 주므로 한국 시간(+09:00)으로 바꾼다. 미결제면 0
    private static OffsetDateTime toDateTime(Long epochSecond) {
        if (epochSecond == null || epochSecond == 0) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), TimeUtils.KST);
    }
}