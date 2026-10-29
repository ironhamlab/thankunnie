package com.ucamp.app.domain.reservation.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 사장님 메인 대시보드. 날짜는 서버 시각(한국 시간) 기준이며 월간 지표는 시술일 기준이다.
 *
 * @param date  기준 날짜 (오늘)
 * @param today 오늘 지표
 * @param month 이번 달 지표
 */
public record ReservationDashboardResponse(LocalDate date, Today today, Month month) {

    /**
     * @param confirmedCount      오늘 시작하는 확정 예약
     * @param upcomingCount       그중 아직 시작하지 않은 예약
     * @param availableStartTimes 지금 예약할 수 있는 30분 단위 시작 시각, 오름차순. 고객 예약 화면과 같은 기준
     *                            (영업시간·열기·닫기·기존 예약·접수 마감)이며, 각 시각부터 30분이 비어 있다
     * @param availableRanges     위 시각을 연속 구간으로 묶은 것. 끝은 마지막 시각 + 30분
     */
    public record Today(
            int confirmedCount,
            int upcomingCount,
            List<OffsetDateTime> availableStartTimes,
            List<AvailableRange> availableRanges) {
    }

    /**
     * 연속으로 비어 있는 시간 구간 [startsAt, endsAt).
     */
    public record AvailableRange(OffsetDateTime startsAt, OffsetDateTime endsAt) {
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
