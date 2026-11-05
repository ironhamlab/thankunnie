package com.ucamp.app.domain.schedule.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 요일을 휴무로 바꾸기 전에 사장님에게 보여줄 영향. 저장 전 확인창에서 쓴다.
 *
 * @param reservations   그 요일의 앞으로의 예약. 휴무로 바꿔도 유지된다
 * @param openExceptions 그 요일에 걸친 앞으로의 시간대 열기. 지우지 않으면 휴무여도 계속 판매된다
 */
public record BusinessHourClosingImpactResponse(
        List<ReservationTime> reservations,
        List<OpenException> openExceptions) {

    public record ReservationTime(OffsetDateTime startsAt, OffsetDateTime endsAt) {
    }

    /**
     * @param closable 함께 닫을 수 있으면 true. 예약이 있거나 휴무로 바꾸지 않는 요일까지 이어지면 false
     */
    public record OpenException(Long exceptionId, OffsetDateTime startsAt, OffsetDateTime endsAt, boolean closable) {
    }
}
