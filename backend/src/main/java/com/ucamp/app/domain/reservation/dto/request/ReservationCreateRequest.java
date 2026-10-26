package com.ucamp.app.domain.reservation.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 예약 생성(예약 클릭) 요청.
 *
 * @param treatmentId     시술 ID
 * @param startsAt        시작 일시 (ISO-8601, 예: 2026-10-24T14:00:00+09:00), 30분 단위
 * @param customerRequest 고객 요청사항, 선택
 */
public record ReservationCreateRequest(
        @NotNull Long treatmentId,
        @NotNull OffsetDateTime startsAt,
        @Size(max = 1000) String customerRequest) {
}
