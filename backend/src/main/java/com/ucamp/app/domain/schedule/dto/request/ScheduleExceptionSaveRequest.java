package com.ucamp.app.domain.schedule.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ucamp.app.common.policy.ScheduleExceptionType;

/**
 * 시간대 열기·닫기 등록·수정 요청.
 *
 * @param exceptionType OPEN(추가로 열기) / CLOSE(닫기)
 * @param startsAt      시작 시각, 30분 단위 (예: 2026-10-25T12:00:00+09:00)
 * @param endsAt        종료 시각, 30분 단위, 구간에 포함하지 않음
 * @param reason        사유 (예: 추석 휴무)
 */
public record ScheduleExceptionSaveRequest(
        @NotNull ScheduleExceptionType exceptionType,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        @Size(max = 500) String reason) {
}
