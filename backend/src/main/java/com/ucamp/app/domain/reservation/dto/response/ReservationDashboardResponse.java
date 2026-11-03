package com.ucamp.app.domain.reservation.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 사장님 홈 대시보드(오늘). 날짜는 서버 시각(한국 시간) 기준이다. 기간 분석은 통계 API 가 맡는다.
 *
 * @param date                   기준 날짜 (오늘)
 * @param today                  오늘 지표
 * @param tomorrowConfirmedCount 내일 시작하는 확정 예약
 * @param monthToDate            이번 달 1일~오늘 이용 추정 금액과 지난달 같은 기간 비교
 * @param favoriteCustomerCount  이 매장을 찜한 고객 수
 */
public record ReservationDashboardResponse(
        LocalDate date,
        Today today,
        int tomorrowConfirmedCount,
        MonthToDate monthToDate,
        long favoriteCustomerCount) {

    /**
     * @param confirmedCount   오늘 시작하는 확정 예약
     * @param upcomingCount    그중 아직 시작하지 않은 예약
     * @param closed           오늘 열린 시간이 전혀 없는 휴무일인지. false 인데 빈 시간이 없으면 남은 시간이 모두 찬 것이다
     * @param availableMinutes 지금 예약받을 수 있는 남은 빈 시간(분) 합계
     * @param availableRanges  남은 빈 시간대. 고객 예약 화면과 같은 기준(영업시간·열기·닫기·기존 예약·접수 마감)
     */
    public record Today(
            int confirmedCount,
            int upcomingCount,
            boolean closed,
            long availableMinutes,
            List<AvailableRange> availableRanges) {
    }

    /**
     * 연속으로 비어 있는 시간대 [startsAt, endsAt).
     */
    public record AvailableRange(OffsetDateTime startsAt, OffsetDateTime endsAt) {
    }

    /**
     * 이번 달 누적. 지난달은 1일부터 오늘과 같은 날(지난달이 더 짧으면 말일)까지 비교한다.
     *
     * @param completedAmount         이번 달 1일~오늘, 지금까지 끝난 확정 예약의 적용가 합계
     * @param previousCompletedAmount 지난달 같은 기간의 같은 금액
     * @param changeRate              증감률(%), 소수 첫째 자리. 지난달 금액이 0 이면 null
     */
    public record MonthToDate(
            LocalDate from,
            LocalDate to,
            long completedAmount,
            LocalDate previousFrom,
            LocalDate previousTo,
            long previousCompletedAmount,
            Double changeRate) {
    }
}
