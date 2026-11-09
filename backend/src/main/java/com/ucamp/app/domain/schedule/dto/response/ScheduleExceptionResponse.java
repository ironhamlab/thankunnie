package com.ucamp.app.domain.schedule.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;
import com.ucamp.app.domain.schedule.entity.ShopScheduleException;

/**
 * 시간대 열기·닫기 일정.
 *
 * @param reservationId 매장이 예약을 취소하며 닫은 경우의 예약 ID, 그 외 null
 */
public record ScheduleExceptionResponse(
        Long exceptionId,
        ScheduleExceptionType exceptionType,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String reason,
        Long reservationId) {

    public static ScheduleExceptionResponse from(ShopScheduleException exception) {
        return new ScheduleExceptionResponse(
                exception.getId(),
                exception.getExceptionType(),
                TimeUtils.toOffset(exception.getStartsAt()),
                TimeUtils.toOffset(exception.getEndsAt()),
                exception.getReason(),
                exception.getReservationId());
    }
}
