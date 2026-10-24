package com.ucamp.app.domain.vacancy.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 25);
    private static final LocalDateTime FAR_BEFORE = DATE.minusDays(3).atTime(9, 0);
    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final TimeRange BUSINESS_HOURS = range("10:00", "20:00");

    @Test
    @DisplayName("영업시간 안에서 시술이 끝나는 30분 단위 시작 시각만 반환한다")
    void availableStartTimes_businessHoursOnly_returnsSlotsEndingBeforeClose() {
        List<LocalDateTime> result = AvailabilityCalculator.availableStartTimes(
                DATE, ONE_HOUR, List.of(BUSINESS_HOURS), List.of(), FAR_BEFORE);

        assertThat(result).hasSize(19);
        assertThat(result.get(0)).isEqualTo(at("10:00"));
        assertThat(result.get(result.size() - 1)).isEqualTo(at("19:00"));
    }

    @Test
    @DisplayName("닫기·예약과 겹치는 시작 시각은 빼고 끝 시각에 맞닿는 시각은 허용한다")
    void availableStartTimes_blocked_excludesOverlaps() {
        List<TimeRange> blocked = List.of(range("12:00", "13:00"), range("15:00", "16:00"));

        List<LocalDateTime> result = AvailabilityCalculator.availableStartTimes(
                DATE, ONE_HOUR, List.of(BUSINESS_HOURS), blocked, FAR_BEFORE);

        assertThat(result).contains(at("11:00"), at("13:00"), at("14:00"), at("16:00"))
                .doesNotContain(at("11:30"), at("12:00"), at("12:30"), at("14:30"), at("15:00"), at("15:30"));
    }

    @Test
    @DisplayName("영업시간에 이어지는 열기는 하나로 합쳐 경계를 넘는 시술도 허용한다")
    void availableStartTimes_adjacentOpen_mergesRanges() {
        List<LocalDateTime> result = AvailabilityCalculator.availableStartTimes(
                DATE, ONE_HOUR, List.of(BUSINESS_HOURS, range("20:00", "22:00")), List.of(), FAR_BEFORE);

        assertThat(result).contains(at("19:30"), at("21:00")).doesNotContain(at("21:30"));
    }

    @Test
    @DisplayName("떨어진 두 열린 구간 사이에 걸친 시술은 허용하지 않는다")
    void isFree_rangeAcrossGap_returnsFalse() {
        List<TimeRange> open = List.of(range("10:00", "12:00"), range("13:00", "18:00"));

        assertThat(AvailabilityCalculator.isFree(range("11:30", "12:30"), open, List.of())).isFalse();
        assertThat(AvailabilityCalculator.isFree(range("13:00", "14:00"), open, List.of())).isTrue();
    }

    @Test
    @DisplayName("열기와 닫기가 겹치면 닫기가 우선한다")
    void isFree_openAndCloseOverlap_closeWins() {
        List<TimeRange> open = List.of(range("20:00", "22:00"));
        List<TimeRange> blocked = List.of(range("21:00", "22:00"));

        assertThat(AvailabilityCalculator.isFree(range("20:00", "21:00"), open, blocked)).isTrue();
        assertThat(AvailabilityCalculator.isFree(range("20:30", "21:30"), open, blocked)).isFalse();
    }

    @Test
    @DisplayName("휴무일(열린 구간 없음)은 시작 시각이 없다")
    void availableStartTimes_noOpenRange_returnsEmpty() {
        assertThat(AvailabilityCalculator.availableStartTimes(DATE, ONE_HOUR, List.of(), List.of(), FAR_BEFORE))
                .isEmpty();
    }

    @Test
    @DisplayName("시작 20분 전이 지난 시각은 빈 시간이어도 반환하지 않는다")
    void availableStartTimes_nearNow_excludesClosedForBooking() {
        LocalDateTime now = at("13:45");

        List<LocalDateTime> result = AvailabilityCalculator.availableStartTimes(
                DATE, ONE_HOUR, List.of(BUSINESS_HOURS), List.of(), now);

        assertThat(result.get(0)).isEqualTo(at("14:30"));
    }

    private static LocalDateTime at(String time) {
        return DATE.atTime(LocalTime.parse(time));
    }

    private static TimeRange range(String start, String end) {
        return new TimeRange(at(start), at(end));
    }
}
