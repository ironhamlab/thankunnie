package com.ucamp.app.domain.reservation.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationView;

/**
 * 예약 목록 항목. 고객 내 예약·매장 예약 현황·관리자 조회가 같은 모양을 쓴다.
 *
 * @param balance       현장 결제 잔금 (적용 판매가 - 예약금)
 * @param paymentStatus 최근 결제 상태 (READY, PAID, REFUND_PENDING, REFUNDED 등), 결제 전이면 null
 * @param cancelStatus  취소 처리 상태 (REQUESTED, COMPLETED 등), 취소 전이면 null
 * @param cancellable   취소 버튼 표시 여부. 확정 상태이고 시작 전이면 true (실제 취소 가능 판단은 취소 API 가 한다)
 */
public record ReservationSummaryResponse(
        Long reservationId,
        ReservationStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Long shopId,
        String shopName,
        Long treatmentId,
        String treatmentName,
        String customerName,
        int appliedPrice,
        int deposit,
        int balance,
        String paymentStatus,
        String cancelStatus,
        boolean cancellable) {

    public static ReservationSummaryResponse from(ReservationView view, boolean cancellable) {
        return new ReservationSummaryResponse(
                view.reservationId(),
                view.status(),
                TimeUtils.toOffset(view.startsAt()),
                TimeUtils.toOffset(view.endsAt()),
                view.shopId(),
                view.shopName(),
                view.treatmentId(),
                view.treatmentName(),
                view.customerName(),
                view.appliedPrice(),
                view.deposit(),
                view.appliedPrice() - view.deposit(),
                view.paymentStatus(),
                view.cancelStatus(),
                cancellable);
    }
}
