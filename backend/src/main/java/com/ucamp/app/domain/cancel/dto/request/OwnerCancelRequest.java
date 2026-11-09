package com.ucamp.app.domain.cancel.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.ucamp.app.domain.cancel.entity.CancelType;

/**
 * 매장 취소 요청 (CNL-15~19, REO-02).
 *
 * @param cancelType       SHOP_FAULT(매장 사유, 전액 환불) 또는 CUSTOMER_REQUEST(고객 요청 대리 취소)
 * @param refundAmount     CUSTOMER_REQUEST 일 때 필수. 견적의 policyRefundAmount 이상 paidAmount 이하. SHOP_FAULT 는 무시하고 전액 환불
 * @param closeAfterCancel true 면 취소한 시간을 닫고, false 면 다시 예약 가능하게 연다
 * @param reason           취소 사유. 닫기를 고르면 예외 일정 사유로도 남는다
 */
public record OwnerCancelRequest(
        @NotNull CancelType cancelType,
        @PositiveOrZero Integer refundAmount,
        @NotNull Boolean closeAfterCancel,
        @Size(max = 500) String reason) {
}
