package com.ucamp.app.domain.cancel.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 고객 직접 취소 요청.
 *
 * @param expectedRefundAmount 화면 견적의 환불액(refundAmount). 실행 시점 재계산 금액과 다르면 CANCEL_007 로 재확인을 받는다
 */
public record CustomerCancelRequest(
        @NotNull @PositiveOrZero Integer expectedRefundAmount) {
}
