package com.ucamp.app.domain.payment.dto.response;

import com.ucamp.app.domain.payment.client.PortOnePayment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record PaymentVerifyResponse(
        String impUid,
        String merchantUid,
        Long amount,
        String status,
        String payMethod,
        LocalDateTime paidAt) {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    public static PaymentVerifyResponse from(PortOnePayment payment) {
        return new PaymentVerifyResponse(
                payment.impUid(),
                payment.merchantUid(),
                payment.amount(),
                payment.status(),
                payment.payMethod(),
                toDateTime(payment.paidAt()));
    }

    private static LocalDateTime toDateTime(Long epochSecond) {
        if (epochSecond == null || epochSecond == 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZONE_SEOUL);
    }
}
