package com.ucamp.app.domain.reservation.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * 예약 생성(예약 클릭) 요청. 고객 요청사항은 결제 화면에서 받아 예약 확정 때 저장한다.
 *
 * @param treatmentId 시술 ID
 * @param startsAt    시작 일시 (ISO-8601, 예: 2026-10-24T14:00:00+09:00), 30분 단위
 */
public record ReservationCreateRequest(
        @NotNull Long treatmentId,
        @NotNull OffsetDateTime startsAt) {
}
