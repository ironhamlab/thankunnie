package com.ucamp.app.domain.schedule.entity;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 영업시간 요일. DDL 의 SHOP_BUSINESS_HOUR.DAY_OF_WEEK 값(MON ~ SUN)과 같다.
 */
public enum BusinessDay {
    MON,
    TUE,
    WED,
    THU,
    FRI,
    SAT,
    SUN;

    public static BusinessDay of(LocalDate date) {
        return of(date.getDayOfWeek());
    }

    public static BusinessDay of(DayOfWeek dayOfWeek) {
        return values()[dayOfWeek.getValue() - 1];
    }
}
