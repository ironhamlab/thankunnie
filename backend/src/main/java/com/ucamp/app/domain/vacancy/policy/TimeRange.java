package com.ucamp.app.domain.vacancy.policy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 시간 구간 [start, end). 종료 시각은 포함하지 않는다.
 */
public record TimeRange(LocalDateTime start, LocalDateTime end) {

    public TimeRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    public boolean overlaps(TimeRange other) {
        return SalePolicy.overlaps(start, end, other.start, other.end);
    }

    public boolean contains(TimeRange other) {
        return !other.start.isBefore(start) && !other.end.isAfter(end);
    }

    /** 겹치거나 맞닿는 구간을 합쳐 시간순으로 반환한다 */
    public static List<TimeRange> union(Collection<TimeRange> ranges) {
        List<TimeRange> sorted = ranges.stream().sorted(Comparator.comparing(TimeRange::start)).toList();
        List<TimeRange> merged = new ArrayList<>();
        for (TimeRange range : sorted) {
            int last = merged.size() - 1;
            if (last >= 0 && !range.start.isAfter(merged.get(last).end)) {
                TimeRange previous = merged.get(last);
                if (range.end.isAfter(previous.end)) {
                    merged.set(last, new TimeRange(previous.start, range.end));
                }
                continue;
            }
            merged.add(range);
        }
        return merged;
    }

    /** ranges 합집합에서 removed 와 겹치는 부분을 뺀 구간을 시간순으로 반환한다 */
    public static List<TimeRange> subtract(Collection<TimeRange> ranges, Collection<TimeRange> removed) {
        List<TimeRange> result = union(ranges);
        for (TimeRange cut : union(removed)) {
            List<TimeRange> next = new ArrayList<>();
            for (TimeRange range : result) {
                if (!range.overlaps(cut)) {
                    next.add(range);
                    continue;
                }
                if (range.start.isBefore(cut.start)) {
                    next.add(new TimeRange(range.start, cut.start));
                }
                if (range.end.isAfter(cut.end)) {
                    next.add(new TimeRange(cut.end, range.end));
                }
            }
            result = next;
        }
        return result;
    }
}
