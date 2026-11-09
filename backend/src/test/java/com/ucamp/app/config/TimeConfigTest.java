package com.ucamp.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.util.TimeUtils;

class TimeConfigTest {

    // UTC 14:59:59 = KST 23:59:59 (자정 직전 경계)
    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-24T14:59:59Z");

    private final TimeConfig timeConfig = new TimeConfig();

    @Test
    @DisplayName("기본 Clock은 Asia/Seoul 시간대를 사용한다")
    void clock_default_isKst() {
        Clock clock = timeConfig.clock();

        assertThat(clock.getZone()).isEqualTo(TimeUtils.KST);
    }

    @Test
    @DisplayName("Auditing 시각은 주입된 Clock 기준의 한국 시각이다")
    void auditingDateTimeProvider_fixedClock_returnsKstLocalDateTime() {
        Clock fixed = Clock.fixed(FIXED_INSTANT, TimeUtils.KST);

        Optional<TemporalAccessor> now = timeConfig.auditingDateTimeProvider(fixed).getNow();

        assertThat(now).contains(LocalDateTime.of(2026, 10, 24, 23, 59, 59));
    }

    @Test
    @DisplayName("LocalDateTime은 +09:00 오프셋의 OffsetDateTime으로 변환된다")
    void toOffset_localDateTime_hasKstOffset() {
        LocalDateTime local = LocalDateTime.of(2026, 10, 24, 23, 59, 59);

        OffsetDateTime offset = TimeUtils.toOffset(local);

        assertThat(offset.getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(offset.toLocalDateTime()).isEqualTo(local);
    }

    @Test
    @DisplayName("null은 null로 변환된다")
    void toOffset_null_returnsNull() {
        assertThat(TimeUtils.toOffset(null)).isNull();
    }
}