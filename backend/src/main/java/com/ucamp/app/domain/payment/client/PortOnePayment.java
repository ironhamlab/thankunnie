package com.ucamp.app.domain.payment.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 포트원 결제 단건 조회 결과 중 검증에 필요한 필드만 매핑한다.
 */
public record PortOnePayment(
        @JsonProperty("imp_uid") String impUid,
        @JsonProperty("merchant_uid") String merchantUid,
        Long amount,
        String status,                       // ready, paid, cancelled, failed
        @JsonProperty("pay_method") String payMethod,
        @JsonProperty("paid_at") Long paidAt // Unix 초 단위, 미결제면 0
) {
}
