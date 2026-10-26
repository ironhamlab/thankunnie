package com.ucamp.app.domain.payment.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 결제 취소 요청. 환불 금액은 서버가 계산하므로 받지 않는다(남은 금액 전액 환불).
 */
public record PaymentCancelRequest(
        @Size(max = 200) String reason
) {

    private static final String DEFAULT_REASON = "사용자 요청 취소";

    public String reasonOrDefault() {
        return (reason == null || reason.isBlank()) ? DEFAULT_REASON : reason;
    }
}
