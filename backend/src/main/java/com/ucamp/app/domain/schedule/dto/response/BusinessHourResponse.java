package com.ucamp.app.domain.schedule.dto.response;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.ucamp.app.domain.schedule.entity.BusinessDay;
import com.ucamp.app.domain.schedule.entity.ShopBusinessHour;

/**
 * 한 요일의 영업시간. 휴무면 시각은 null.
 */
public record BusinessHourResponse(
        BusinessDay dayOfWeek,
        boolean closed,
        @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closeTime) {

    public static BusinessHourResponse from(ShopBusinessHour hour) {
        return new BusinessHourResponse(hour.getDayOfWeek(), hour.isClosed(), hour.getOpenTime(),
                hour.getCloseTime());
    }

    /** 아직 저장하지 않은 요일은 휴무로 보여준다 */
    public static BusinessHourResponse closedDay(BusinessDay dayOfWeek) {
        return new BusinessHourResponse(dayOfWeek, true, null, null);
    }
}
