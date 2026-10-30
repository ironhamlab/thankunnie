package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;

import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 대시보드·통계 집계용 예약 행.
 *
 * @param status            CONFIRMED 또는 CANCELLED
 * @param holdExpiresAt     점유 만료 시각 (점유 시작 = 이 값 - 점유 시간)
 * @param appliedPrice      점유 시점 적용가
 * @param cancelType        취소 유형. 취소 행이 없으면 null (확정 예약에도 실패한 취소 행이 있을 수 있다)
 * @param cancelCompletedAt 결제 취소 완료 시각
 * @param retainedDeposit   취소 완료 건의 납부 예약금 - 환불액. 확정 예약은 0
 * @param closedAfterCancel 사장님이 취소하며 그 시간을 닫았는지
 */
public record ReservationDashboardRow(
        Long reservationId,
        Long treatmentId,
        String treatmentName,
        ReservationStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime holdExpiresAt,
        LocalDateTime createdAt,
        int appliedPrice,
        CancelType cancelType,
        LocalDateTime cancelCompletedAt,
        int retainedDeposit,
        boolean closedAfterCancel) {

    public boolean confirmed() {
        return status == ReservationStatus.CONFIRMED;
    }

    public boolean cancelled() {
        return status == ReservationStatus.CANCELLED;
    }
}
