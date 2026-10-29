package com.ucamp.app.domain.reservation.dto.response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 사장님 통계 화면. 모든 지표는 시술일 기준이며 확정(CONFIRMED)·취소 완료(CANCELLED) 예약만 센다.
 *
 * @param from       집계 시작 날짜 (포함)
 * @param to         집계 끝 날짜 (포함)
 * @param summary    기간 합계
 * @param daily      날짜별 추이. 예약이 없는 날도 0 으로 채워 from~to 를 모두 담는다
 * @param treatments 시술별 성과. 매출 많은 순
 * @param timeSlots  요일·시작 시각(시)별 확정 건수. 0 인 칸은 뺀다
 */
public record ReservationStatsResponse(
        LocalDate from,
        LocalDate to,
        Summary summary,
        List<Daily> daily,
        List<Treatment> treatments,
        List<TimeSlot> timeSlots) {

    /**
     * @param confirmedCount  확정 예약
     * @param sales           확정 예약 적용가 합계 (잔금 포함 예상 매출)
     * @param cancelledCount  취소 완료
     * @param rebookedCount   취소로 다시 열린 시간에 들어온 확정 예약
     * @param rebookedSales   그 예약들의 적용가 합계
     * @param lastMinuteCount 초임박 구간(시작 3시간 이내)에 점유한 확정 예약
     * @param lastMinuteSales 그 예약들의 적용가 합계
     */
    public record Summary(
            int confirmedCount,
            long sales,
            int cancelledCount,
            int rebookedCount,
            long rebookedSales,
            int lastMinuteCount,
            long lastMinuteSales) {
    }

    public record Daily(LocalDate date, int confirmedCount, long sales, int cancelledCount) {
    }

    public record Treatment(Long treatmentId, String treatmentName, int confirmedCount, long sales,
            int cancelledCount) {
    }

    public record TimeSlot(DayOfWeek dayOfWeek, int hour, int confirmedCount) {
    }
}
