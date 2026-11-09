package com.ucamp.app.domain.reservation.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * 예약 대기열에 서거나 나갈 빈자리. 예약 생성 요청과 같은 시술·시작 일시로 줄을 나눈다.
 *
 * @param treatmentId 시술 ID
 * @param startsAt    시작 일시 (ISO-8601, 예: 2026-10-24T14:00:00+09:00)
 */
public record ReservationQueueRequest(
        @NotNull Long treatmentId,
        @NotNull OffsetDateTime startsAt) {
}
