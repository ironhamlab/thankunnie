package com.ucamp.app.domain.vacancy.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 날짜별 예약 시작 가능 시각.
 *
 * @param durationMinutes 시술 소요 시간(분). 이 시간만큼 연속으로 비어 있는 시작 시각만 담는다
 * @param startTimes      30분 단위 시작 가능 시각, 오름차순
 * @param extendedStartTimes startTimes 중 영업시간 밖 연장 영업(사장님이 연 시간) 덕분에 가능한 시각, 오름차순.
 *                        시술이 영업시간 밖으로 조금이라도 넘어가면 포함한다. 화면의 연장 영업 표시용
 * @param heldStartTimes  다른 고객이 결제 중(점유)이라 지금은 시작할 수 없지만 예약 대기열에 설 수 있는 시각, 오름차순.
 *                        startTimes 와 겹치지 않는다
 */
public record AvailableTimesResponse(
        Long shopId,
        LocalDate date,
        int durationMinutes,
        List<OffsetDateTime> startTimes,
        List<OffsetDateTime> extendedStartTimes,
        List<OffsetDateTime> heldStartTimes) {
}
