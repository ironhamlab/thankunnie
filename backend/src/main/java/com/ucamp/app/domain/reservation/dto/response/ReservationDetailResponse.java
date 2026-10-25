package com.ucamp.app.domain.reservation.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.reservation.repository.ReservationView;

/**
 * 예약 상세. 고객 내 예약·사장님 예약 현황·관리자 조회가 같은 모양을 쓴다.
 *
 * @param summary         목록과 같은 요약
 * @param customerPhone   예약자 연락처
 * @param customerRequest 고객 요청사항
 * @param holdExpiresAt   점유 만료 시각
 * @param confirmedAt     예약 확정 시각, 확정 전이면 null
 * @param reservedAt      예약 클릭(생성) 시각
 * @param payment         최근 결제, 결제 전이면 null
 * @param cancel          취소, 취소 전이면 null
 */
public record ReservationDetailResponse(
        ReservationSummaryResponse summary,
        String customerPhone,
        String customerRequest,
        OffsetDateTime holdExpiresAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime reservedAt,
        Payment payment,
        Cancel cancel) {

    public static ReservationDetailResponse from(ReservationView view, boolean cancellable) {
        Payment payment = view.paymentStatus() == null ? null : new Payment(
                view.paymentStatus(),
                view.paymentAmount(),
                view.refundedAmount(),
                TimeUtils.toOffset(view.approvedAt()),
                TimeUtils.toOffset(view.refundedAt()));
        Cancel cancel = view.cancelStatus() == null ? null : new Cancel(
                view.cancelType(),
                view.cancelStatus(),
                view.policyRefundAmount(),
                view.refundAmount(),
                TimeUtils.toOffset(view.cancelRequestedAt()),
                TimeUtils.toOffset(view.cancelCompletedAt()));
        return new ReservationDetailResponse(
                ReservationSummaryResponse.from(view, cancellable),
                view.customerPhone(),
                view.customerRequest(),
                TimeUtils.toOffset(view.holdExpiresAt()),
                TimeUtils.toOffset(view.confirmedAt()),
                TimeUtils.toOffset(view.createdAt()),
                payment,
                cancel);
    }

    /**
     * 예약금 결제.
     *
     * @param status         READY, PAID, FAILED, UNKNOWN, REFUND_PENDING, PART_REFUNDED, REFUNDED
     * @param amount         결제 금액
     * @param refundedAmount 환불 완료 금액
     */
    public record Payment(
            String status,
            Integer amount,
            Integer refundedAmount,
            OffsetDateTime approvedAt,
            OffsetDateTime refundedAt) {
    }

    /**
     * 예약 취소.
     *
     * @param cancelType         CUSTOMER, SHOP_FAULT, CUSTOMER_REQUEST
     * @param status             REQUESTED, COMPLETED, FAILED, UNKNOWN
     * @param policyRefundAmount 정책상 환불액
     * @param refundAmount       환불 예정 금액
     */
    public record Cancel(
            String cancelType,
            String status,
            Integer policyRefundAmount,
            Integer refundAmount,
            OffsetDateTime requestedAt,
            OffsetDateTime completedAt) {
    }
}
