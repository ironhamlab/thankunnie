package com.ucamp.app.domain.reservation.repository;

/**
 * 대시보드 예약 건수 집계 결과.
 *
 * @param todayConfirmedCount 오늘 시작하는 확정 예약
 * @param todayUpcomingCount  오늘 확정 예약 중 아직 시작하지 않은 예약
 * @param confirmedCount      기간 안 확정 예약
 * @param expectedSales       기간 안 확정 예약의 적용가 합계 (잔금 포함 예상 매출)
 * @param cancelPendingCount  기간 안 취소 처리 중 예약
 */
public record ReservationDashboardCounts(
        int todayConfirmedCount,
        int todayUpcomingCount,
        int confirmedCount,
        long expectedSales,
        int cancelPendingCount) {
}
