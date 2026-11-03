package com.ucamp.app.domain.vacancy.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 날짜별 예약 시작 가능 시각.
 *
 * @param durationMinutes 시술 소요 시간(분). 이 시간만큼 연속으로 비어 있는 시작 시각만 담는다
 * @param startTimes      30분 단위 시작 가능 시각, 오름차순
 * @param extendedStartTimes startTimes 중 영업시간 밖 연장 영업(매장이 연 시간) 덕분에 가능한 시각, 오름차순.
 *                        시술이 영업시간 밖으로 조금이라도 넘어가면 포함한다. 화면의 연장 영업 표시용
 */
public record AvailableTimesResponse(
        Long shopId,
        LocalDate date,
        int durationMinutes,
        List<OffsetDateTime> startTimes,
        List<OffsetDateTime> extendedStartTimes) {
}
