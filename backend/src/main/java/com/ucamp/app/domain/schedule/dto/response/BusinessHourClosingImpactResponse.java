package com.ucamp.app.domain.schedule.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 요일을 휴무로 바꾸기 전에 사장님에게 보여줄 영향. 저장 전 확인창에서 쓴다.
 *
 * @param reservations   그 요일의 앞으로의 예약. 휴무로 바꿔도 유지된다
 * @param openExceptions 그 요일에 등록한 앞으로의 시간대 열기. 지우지 않으면 휴무여도 계속 판매된다
 */
public record BusinessHourClosingImpactResponse(
        List<ReservationTime> reservations,
        List<OpenException> openExceptions) {

    public record ReservationTime(OffsetDateTime startsAt, OffsetDateTime endsAt) {
    }

    /**
     * @param reserved 이 시간에 예약이 있어 함께 닫을 수 없으면 true
     */
    public record OpenException(Long exceptionId, OffsetDateTime startsAt, OffsetDateTime endsAt, boolean reserved) {
    }
}
