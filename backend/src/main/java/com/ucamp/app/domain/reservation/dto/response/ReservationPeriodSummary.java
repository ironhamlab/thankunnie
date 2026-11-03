package com.ucamp.app.domain.reservation.dto.response;

import java.time.LocalDate;

/**
 * 기간 요약 지표. 시술일(시작 날짜) 기준이며 확정·취소 완료 예약만 센다.
 * 금액은 예약 시점 적용가 기준이라 현장 할인·추가 결제는 반영되지 않는다.
 *
 * @param from                     집계 시작 날짜 (포함)
 * @param to                       집계 끝 날짜 (포함)
 * @param completedCount           이용 추정 예약: 확정 예약 중 종료 시각이 지난 예약
 * @param completedAmount          그 예약들의 적용가 합계
 * @param upcomingCount            예정 예약: 확정 예약 중 아직 끝나지 않은 예약
 * @param upcomingAmount           그 예약들의 적용가 합계
 * @param averageAmount            객단가. 이용 추정 금액 ÷ 이용 추정 건수 (원 단위 반올림). 이용이 없으면 null
 * @param customerCancelCount      고객 사정 취소 (고객 직접 취소 + 고객 요청 대리 취소)
 * @param shopFaultCancelCount     매장 사유 취소
 * @param customerCancelRate       고객 취소율(%). 고객 취소 ÷ (확정 + 고객 취소 + 매장 사유 취소), 소수 첫째 자리. 대상이 없으면 null
 * @param nonRefundedDepositAmount 취소로 환불되지 않은 예약금 (취소 완료 건의 납부 예약금 - 환불액 합계)
 * @param cancelledMinutes         취소로 비었던 시간(분). 사장님이 취소하며 닫은 시간은 뺀다
 * @param rebookedMinutes          그중 다시 채워진 시간(분)
 * @param rebookRate               회수율(%). 다시 채운 시간 ÷ 비었던 시간, 소수 첫째 자리. 비었던 시간이 없으면 null
 * @param rebookedBookingAmount    비었던 시간에 다시 들어온 확정 예약의 적용가 합계 (겹친 예약 전체 금액이라 손실 회수액과 같지 않다)
 * @param lastMinuteCount          초임박(시작 3시간 이내)에 점유한 확정 예약
 * @param lastMinuteAmount         그 예약들의 적용가 합계
 * @param customerCount            기간 안에 이용한 고객 수
 * @param newCustomerCount         그중 신규 고객: 기간 끝 시점까지 이 매장 누적 이용이 1회
 * @param returningCustomerCount   그중 재방문 고객: 기간 끝 시점까지 이 매장 누적 이용이 2회 이상
 * @param revisitRate              재방문율(%). 재방문 고객 ÷ 이용 고객, 소수 첫째 자리. 이용 고객이 없으면 null
 */
public record ReservationPeriodSummary(
        LocalDate from,
        LocalDate to,
        int completedCount,
        long completedAmount,
        int upcomingCount,
        long upcomingAmount,
        Long averageAmount,
        int customerCancelCount,
        int shopFaultCancelCount,
        Double customerCancelRate,
        long nonRefundedDepositAmount,
        long cancelledMinutes,
        long rebookedMinutes,
        Double rebookRate,
        long rebookedBookingAmount,
        int lastMinuteCount,
        long lastMinuteAmount,
        int customerCount,
        int newCustomerCount,
        int returningCustomerCount,
        Double revisitRate) {
}
