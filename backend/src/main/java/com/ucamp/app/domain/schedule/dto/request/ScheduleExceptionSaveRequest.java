package com.ucamp.app.domain.schedule.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;

/**
 * 시간대 열기·닫기 등록·수정 요청.
 *
 * @param exceptionType OPEN(추가로 열기) / CLOSE(닫기)
 * @param startsAt      시작 시각, 30분 단위 (예: 2026-10-25T12:00:00+09:00)
 * @param endsAt        종료 시각, 30분 단위, 구간에 포함하지 않음
 * @param reason        사유 (예: 추석 휴무)
 * @param excludeReserved 닫기 등록에서 true 면 예약이 있는 시간을 빼고 나머지만 닫는다. 없으면 false 로 보고 예약이 있으면 거절한다
 */
public record ScheduleExceptionSaveRequest(
        @NotNull ScheduleExceptionType exceptionType,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        @Size(max = 500) String reason,
        Boolean excludeReserved) {

    public ScheduleExceptionSaveRequest(ScheduleExceptionType exceptionType, OffsetDateTime startsAt,
            OffsetDateTime endsAt, String reason) {
        this(exceptionType, startsAt, endsAt, reason, null);
    }
}
