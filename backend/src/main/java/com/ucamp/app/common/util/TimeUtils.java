package com.ucamp.app.common.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 서비스 기준 시간대(Asia/Seoul) 관련 공통 변환.
 *
 * <p>현재 시각은 이 클래스가 아니라 주입받은 {@link java.time.Clock}으로 구한다.
 */
public final class TimeUtils {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private TimeUtils() {
    }

    /** 엔티티의 LocalDateTime(KST)을 응답용 OffsetDateTime(+09:00)으로 변환한다. */
    public static OffsetDateTime toOffset(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(KST).toOffsetDateTime();
    }

    /**
     * 요청의 OffsetDateTime 을 엔티티용 LocalDateTime(KST)으로 변환한다.
     * 다른 오프셋으로 와도 같은 순간의 한국 시각이 된다.
     */
    public static LocalDateTime toLocal(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.atZoneSameInstant(KST).toLocalDateTime();
    }
}