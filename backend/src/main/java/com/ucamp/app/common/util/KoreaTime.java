package com.ucamp.app.common.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import com.ucamp.app.common.policy.BookingPolicy;

/**
 * API 의 오프셋 포함 시각(+09:00)과 DB 의 한국 시간 LocalDateTime 사이 변환.
 */
public final class KoreaTime {

    private KoreaTime() {
    }

    /** 요청 시각을 한국 시간으로 맞춰 오프셋을 뗀다. 다른 오프셋으로 와도 같은 순간의 한국 시각이 된다 */
    public static LocalDateTime toLocal(OffsetDateTime time) {
        return time == null ? null : time.atZoneSameInstant(BookingPolicy.ZONE).toLocalDateTime();
    }

    /** DB 의 한국 시각에 +09:00 을 붙여 응답한다 */
    public static OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(BookingPolicy.ZONE).toOffsetDateTime();
    }
}
