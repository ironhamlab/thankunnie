package com.ucamp.app.domain.reservation.dto.response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 매장 매출·예약 분석. 시술일 기준이며 확정·취소 완료 예약만 센다.
 *
 * @param granularity      추이 단위. 기간이 62일 이하면 DAY, 넘으면 MONTH
 * @param serviceStartDate 서비스 이용 시작 날짜 (매장 가입일과 첫 예약일 중 이른 날). "전체" 조회의 시작 날짜
 * @param summary          기간 요약 (기간은 summary.from ~ summary.to)
 * @param previous         바로 앞 같은 길이 기간의 요약. 그 기간이 서비스 이용 시작 전이면 null
 * @param trend            기간별 추이. 예약이 없는 구간도 0 으로 채워 기간 전체를 담는다
 * @param byWeekday        요일별 확정 예약. 월요일부터 일요일까지 7개
 * @param heatmap          요일 × 시간대별 영업 시간·확정 예약 점유 시간(분). 둘 다 0 인 칸은 담지 않는다
 * @param treatments       시술별 확정 예약. 금액 많은 순
 */
public record ReservationStatsResponse(
        Granularity granularity,
        LocalDate serviceStartDate,
        ReservationPeriodSummary summary,
        ReservationPeriodSummary previous,
        List<Trend> trend,
        List<Weekday> byWeekday,
        List<HeatmapCell> heatmap,
        List<Treatment> treatments) {

    public enum Granularity {
        DAY, MONTH
    }

    /**
     * 추이 한 칸 [periodStart, periodEnd]. MONTH 단위의 첫·마지막 칸은 조회 기간에 걸친 날짜만 담는다.
     *
     * @param cancelledCount 그 구간에 시작 예정이던 취소 완료 예약 (유형 무관)
     */
    public record Trend(
            LocalDate periodStart,
            LocalDate periodEnd,
            int completedCount,
            long completedAmount,
            int upcomingCount,
            long upcomingAmount,
            int cancelledCount) {
    }

    /**
     * @param count    그 요일에 시작하는 확정 예약 (이용 추정 + 예정)
     * @param amount   그 예약들의 적용가 합계
     * @param openDays 기간 안 그 요일 중 영업한 날 수. 휴무일은 빼고, 예약이 있던 날은 영업한 날로 본다.
     *                 하루 평균의 분모다
     */
    public record Weekday(DayOfWeek dayOfWeek, int count, long amount, int openDays) {
    }

    /**
     * 예약 시간을 시간대(정시~정시)별로 나눠 더한 점유 시간. 13:30~15:00 예약은 13시 30분, 14시 60분이다.
     *
     * @param hour          시작 정시 (0~23)
     * @param openMinutes   기간 안 그 요일·시간대에 영업한 시간(분) 합계. 휴무·영업 외 시간은 빼고,
     *                      예약이 있던 시간은 영업한 시간으로 본다. 점유율의 분모다
     */
    public record HeatmapCell(DayOfWeek dayOfWeek, int hour, long bookedMinutes, long openMinutes) {
    }

    /**
     * @param count  확정 예약 (이용 추정 + 예정)
     * @param amount 적용가 합계
     * @param share  금액 비중(%), 소수 첫째 자리. 전체 금액이 0 이면 null
     */
    public record Treatment(Long treatmentId, String treatmentName, int count, long amount, Double share) {
    }
}
