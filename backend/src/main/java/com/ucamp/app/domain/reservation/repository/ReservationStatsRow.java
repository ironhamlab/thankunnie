package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;

import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 통계 화면 집계용 예약 행.
 *
 * @param treatmentId   시술 ID
 * @param treatmentName 시술명
 * @param status        CONFIRMED 또는 CANCELLED
 * @param startsAt      시작 시각
 * @param holdExpiresAt 점유 만료 시각 (점유 시작 = 이 값 - 점유 시간)
 * @param appliedPrice  점유 시점 적용가
 * @param rebooked      취소로 다시 열린 시간에 들어온 확정 예약인지
 */
public record ReservationStatsRow(
        Long treatmentId,
        String treatmentName,
        ReservationStatus status,
        LocalDateTime startsAt,
        LocalDateTime holdExpiresAt,
        int appliedPrice,
        boolean rebooked) {
}
