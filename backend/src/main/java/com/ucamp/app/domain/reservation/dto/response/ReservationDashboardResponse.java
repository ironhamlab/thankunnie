package com.ucamp.app.domain.reservation.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 사장님 메인 대시보드. 날짜는 서버 시각(한국 시간) 기준이다.
 *
 * @param date  기준 날짜 (오늘)
 * @param today 오늘 지표
 * @param month 이번 달(1일~말일) 요약
 */
public record ReservationDashboardResponse(LocalDate date, Today today, ReservationPeriodSummary month) {

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
}
