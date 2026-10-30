package com.ucamp.app.domain.reservation.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 사장님 통계 화면. 시술일 기준이며 확정·취소 완료 예약만 센다.
 *
 * @param summary    기간 요약 (기간은 summary.from ~ summary.to)
 * @param daily      날짜별 추이. 예약이 없는 날도 0 으로 채워 기간 전체를 담는다
 * @param treatments 시술별 성과. 금액 많은 순
 */
public record ReservationStatsResponse(
        ReservationPeriodSummary summary,
        List<Daily> daily,
        List<Treatment> treatments) {

    /**
     * @param cancelledCount 그날 시작 예정이던 취소 완료 예약 (유형 무관)
     */
    public record Daily(LocalDate date, int confirmedCount, long bookedAmount, int cancelledCount) {
    }

    public record Treatment(Long treatmentId, String treatmentName, int confirmedCount, long bookedAmount) {
    }
}
