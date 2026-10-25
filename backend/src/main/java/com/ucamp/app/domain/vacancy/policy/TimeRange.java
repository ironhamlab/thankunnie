package com.ucamp.app.domain.vacancy.policy;

import java.time.LocalDateTime;
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
}
