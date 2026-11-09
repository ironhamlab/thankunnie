package com.ucamp.app.domain.explore.dto.request;

import java.time.LocalTime;

/**
 * 시작 시각 기준 시간대 필터. 오전은 12시 전, 오후는 12~18시, 저녁은 18시 이후다.
 */
public enum ExploreTimeOfDay {
    MORNING,
    AFTERNOON,
    EVENING;

    private static final LocalTime NOON = LocalTime.NOON;
    private static final LocalTime EVENING_FROM = LocalTime.of(18, 0);

    public boolean contains(LocalTime time) {
        return switch (this) {
            case MORNING -> time.isBefore(NOON);
            case AFTERNOON -> !time.isBefore(NOON) && time.isBefore(EVENING_FROM);
            case EVENING -> !time.isBefore(EVENING_FROM);
        };
    }
}
