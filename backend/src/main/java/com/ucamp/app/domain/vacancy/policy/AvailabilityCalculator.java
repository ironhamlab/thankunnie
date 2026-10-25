package com.ucamp.app.domain.vacancy.policy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.ucamp.app.common.policy.BookingPolicy;

/**
 * 가용 시간 계산 (B-06).
 * 열린 구간 = 영업시간 ∪ 시간대 열기, 막힌 구간 = 시간대 닫기 ∪ 시간을 잡고 있는 예약.
 * 시술 구간 [시작, 시작 + 소요 시간)이 열린 구간 합집합 안에 모두 들어가고 막힌 구간과 겹치지 않으면 예약할 수 있다.
 * 열기와 닫기가 겹치면 닫기가 우선한다.
 */
public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    /**
     * 구간이 열려 있고 막히지 않았는지 판단한다.
     */
    public static boolean isFree(TimeRange range, List<TimeRange> openRanges, List<TimeRange> blockedRanges) {
        return isCovered(range, openRanges) && blockedRanges.stream().noneMatch(range::overlaps);
    }

    /**
     * 날짜의 30분 단위 시작 시각 중 예약 가능한 시각을 오름차순으로 반환한다.
     * 시각 마감(시작 20분 전, 60일 이내)은 SalePolicy.isBookable 로 함께 거른다.
     */
    public static List<LocalDateTime> availableStartTimes(LocalDate date, Duration duration,
            List<TimeRange> openRanges, List<TimeRange> blockedRanges, LocalDateTime now) {
        Duration step = Duration.ofMinutes(BookingPolicy.TIME_UNIT_MINUTES);
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        List<LocalDateTime> startTimes = new ArrayList<>();
        for (LocalDateTime start = date.atStartOfDay(); start.isBefore(endOfDay); start = start.plus(step)) {
            TimeRange range = new TimeRange(start, start.plus(duration));
            if (SalePolicy.isBookable(start, now) && isFree(range, openRanges, blockedRanges)) {
                startTimes.add(start);
            }
        }
        return startTimes;
    }

    // 맞닿거나 겹치는 열린 구간을 합친 뒤, 하나의 합친 구간이 대상 구간을 모두 포함하는지 본다
    private static boolean isCovered(TimeRange range, List<TimeRange> openRanges) {
        List<TimeRange> sorted = openRanges.stream().sorted(Comparator.comparing(TimeRange::start)).toList();
        LocalDateTime mergedStart = null;
        LocalDateTime mergedEnd = null;
        for (TimeRange open : sorted) {
            if (mergedEnd != null && !open.start().isAfter(mergedEnd)) {
                mergedEnd = open.end().isAfter(mergedEnd) ? open.end() : mergedEnd;
                continue;
            }
            if (mergedEnd != null && new TimeRange(mergedStart, mergedEnd).contains(range)) {
                return true;
            }
            mergedStart = open.start();
            mergedEnd = open.end();
        }
        return mergedEnd != null && new TimeRange(mergedStart, mergedEnd).contains(range);
    }
}
