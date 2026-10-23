package com.ucamp.app.domain.vacancy.policy;

import java.time.Duration;
import java.time.LocalDateTime;

import com.ucamp.app.common.policy.BookingPolicy;

/**
 * 시작까지 남은 시간에 따른 가격 구간.
 */
public enum PriceTier {

    /** 시작 8시간 초과 전 */
    NORMAL,
    /** 시작 8시간 전부터 3시간 초과 전까지 */
    IMMINENT,
    /** 시작 3시간 전부터 */
    LAST_MINUTE;

    /**
     * 판단 시점 기준 구간을 반환한다. 구간은 예약 클릭 시점에 한 번 정하고 점유 중에는 바꾸지 않는다.
     * 경계 시각(정확히 8시간·3시간 전)은 더 임박한 구간으로 본다.
     */
    public static PriceTier of(LocalDateTime startsAt, LocalDateTime now) {
        Duration remaining = Duration.between(now, startsAt);
        if (remaining.compareTo(BookingPolicy.IMMINENT_FROM) > 0) {
            return NORMAL;
        }
        if (remaining.compareTo(BookingPolicy.LAST_MINUTE_FROM) > 0) {
            return IMMINENT;
        }
        return LAST_MINUTE;
    }
}
