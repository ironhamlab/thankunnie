package com.ucamp.app.domain.reservation.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * 예약 생성(예약 클릭) 요청. 고객 요청사항은 결제 화면에서 받아 예약 확정 때 저장한다.
 *
 * @param treatmentId 시술 ID
 * @param startsAt    시작 일시 (ISO-8601, 예: 2026-10-24T14:00:00+09:00), 30분 단위
 * @param replaceHold 결제 대기 중인 본인 점유가 있을 때 그 점유를 풀고 이 시간으로 바꿀지. 생략하면 false
 */
public record ReservationCreateRequest(
        @NotNull Long treatmentId,
        @NotNull OffsetDateTime startsAt,
        Boolean replaceHold) {

    public ReservationCreateRequest(Long treatmentId, OffsetDateTime startsAt) {
        this(treatmentId, startsAt, null);
    }

    public boolean replacesHold() {
        return Boolean.TRUE.equals(replaceHold);
    }
}
