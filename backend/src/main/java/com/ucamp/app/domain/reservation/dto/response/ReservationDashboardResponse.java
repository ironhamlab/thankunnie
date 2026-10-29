package com.ucamp.app.domain.reservation.dto.response;

import java.time.LocalDate;

/**
 * 사장님 메인 대시보드. 날짜는 서버 시각(한국 시간) 기준이며 월간 지표는 시술일 기준이다.
 *
 * @param date  기준 날짜 (오늘)
 * @param today 오늘 지표
 * @param month 이번 달 지표
 */
public record ReservationDashboardResponse(LocalDate date, Today today, Month month) {

    /**
     * @param confirmedCount 오늘 시작하는 확정 예약
     * @param upcomingCount  그중 아직 시작하지 않은 예약
     */
    public record Today(int confirmedCount, int upcomingCount) {
    }

    /**
     * @param from                       집계 시작 날짜 (포함)
     * @param to                         집계 끝 날짜 (포함)
     * @param confirmedCount             확정 예약
     * @param expectedSales              확정 예약 적용가 합계 (잔금 포함 예상 매출)
     * @param cancelledCount             취소 완료 합계
     * @param customerCancelCount        고객 직접 취소
     * @param shopFaultCancelCount       매장 사유 취소
     * @param customerRequestCancelCount 고객 요청 대리 취소
     * @param cancelPendingCount         취소 처리 중
     * @param rebookedCount              취소로 다시 열린 시간에 들어온 확정 예약
     * @param rebookedSales              그 예약들의 적용가 합계
     */
    public record Month(
            LocalDate from,
            LocalDate to,
            int confirmedCount,
            long expectedSales,
            int cancelledCount,
            int customerCancelCount,
            int shopFaultCancelCount,
            int customerRequestCancelCount,
            int cancelPendingCount,
            int rebookedCount,
            long rebookedSales) {
    }
}
