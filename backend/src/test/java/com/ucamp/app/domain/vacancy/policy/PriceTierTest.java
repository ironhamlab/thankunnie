package com.ucamp.app.domain.vacancy.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PriceTierTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 18, 0);

    @ParameterizedTest(name = "시작 {0}분 전 → {1}")
    @CsvSource({
            "600, NORMAL",
            "481, NORMAL",
            "480, IMMINENT",
            "181, IMMINENT",
            "180, LAST_MINUTE",
            "15, LAST_MINUTE"
    })
    @DisplayName("시작까지 남은 시간으로 구간을 판단하고 경계 시각은 더 임박한 구간으로 본다")
    void of_remainingMinutes_returnsTier(long minutesBefore, PriceTier expected) {
        LocalDateTime now = STARTS_AT.minusMinutes(minutesBefore);

        assertThat(PriceTier.of(STARTS_AT, now)).isEqualTo(expected);
    }
}
