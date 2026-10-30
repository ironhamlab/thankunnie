package com.ucamp.app.domain.reservation.dto.response;

import java.time.LocalDate;

/**
 * 기간 요약 지표. 시술일(시작 날짜) 기준이며 확정·취소 완료 예약만 센다. 대시보드 이번 달과 통계 화면이 함께 쓴다.
 *
 * @param from                  집계 시작 날짜 (포함)
 * @param to                    집계 끝 날짜 (포함)
 * @param confirmedCount        확정 예약
 * @param bookedAmount          확정 예약 금액 (적용가 합계, 예약금과 현장 잔금 포함)
 * @param customerCancelCount   고객 사정 취소 (고객 직접 취소 + 고객 요청 대리 취소)
 * @param shopFaultCancelCount  매장 사유 취소
 * @param customerCancelRate    고객 취소율(%). 고객 취소 ÷ (확정 + 고객 취소 + 매장 사유 취소), 소수 첫째 자리. 대상이 없으면 null
 * @param retainedDepositAmount 취소로 남은 예약금 (취소 완료 건의 납부 예약금 - 환불액 합계)
 * @param rebookedAmount        취소로 비었던 시간에 다시 들어온 확정 예약의 금액 합계
 * @param cancelledMinutes      취소로 비었던 시간(분). 사장님이 취소하며 닫은 시간은 뺀다
 * @param rebookedMinutes       그중 다시 채워진 시간(분)
 * @param rebookRate            회수율(%). 다시 채운 시간 ÷ 비었던 시간, 소수 첫째 자리. 비었던 시간이 없으면 null
 * @param lastMinuteCount       초임박(시작 3시간 이내)에 점유한 확정 예약
 * @param lastMinuteAmount      그 예약들의 금액 합계
 */
public record ReservationPeriodSummary(
        LocalDate from,
        LocalDate to,
        int confirmedCount,
        long bookedAmount,
        int customerCancelCount,
        int shopFaultCancelCount,
        Double customerCancelRate,
        long retainedDepositAmount,
        long rebookedAmount,
        long cancelledMinutes,
        long rebookedMinutes,
        Double rebookRate,
        int lastMinuteCount,
        long lastMinuteAmount) {
}
