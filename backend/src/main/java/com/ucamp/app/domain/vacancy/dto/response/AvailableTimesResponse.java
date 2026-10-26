package com.ucamp.app.domain.vacancy.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 날짜별 예약 시작 가능 시각.
 *
 * @param durationMinutes 시술 소요 시간(분). 이 시간만큼 연속으로 비어 있는 시작 시각만 담는다
 * @param startTimes      30분 단위 시작 가능 시각, 오름차순
 */
public record AvailableTimesResponse(
        Long shopId,
        LocalDate date,
        int durationMinutes,
        List<OffsetDateTime> startTimes) {
}
