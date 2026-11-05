package com.ucamp.app.domain.schedule.dto.request;

import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.ucamp.app.domain.schedule.entity.BusinessDay;

/**
 * 요일별 영업시간 저장 요청. 월~일 7개 요일을 한 번씩 보낸다.
 *
 * @param days                요일별 영업시간
 * @param closeOpenExceptions true 면 이번에 휴무로 바뀐 요일의 시간대 열기 중 예약이 없는 것을 함께 지운다. 없으면 false
 */
public record BusinessHourSaveRequest(
        @NotNull @Size(min = 7, max = 7) List<@Valid @NotNull Day> days,
        Boolean closeOpenExceptions) {

    public BusinessHourSaveRequest(List<Day> days) {
        this(days, null);
    }

    /**
     * 한 요일의 영업시간.
     *
     * @param dayOfWeek MON ~ SUN
     * @param closed    휴무 여부. 휴무면 시각은 무시한다
     * @param openTime  영업 시작 (HH:mm, 30분 단위)
     * @param closeTime 영업 종료 (HH:mm, 30분 단위, 시작보다 뒤. 자정 넘김은 지원하지 않음)
     */
    public record Day(
            @NotNull BusinessDay dayOfWeek,
            @NotNull Boolean closed,
            @JsonFormat(pattern = "HH:mm") LocalTime openTime,
            @JsonFormat(pattern = "HH:mm") LocalTime closeTime) {
    }
}
