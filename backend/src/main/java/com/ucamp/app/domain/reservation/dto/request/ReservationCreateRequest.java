package com.ucamp.app.domain.reservation.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * 예약 생성(예약 클릭) 요청. 고객 요청사항은 결제 화면에서 받아 예약 확정 때 저장한다.
 *
 * @param treatmentId          시술 ID
 * @param startsAt             시작 일시 (ISO-8601, 예: 2026-10-24T14:00:00+09:00), 30분 단위
 * @param replaceReservationId 풀고 이 시간으로 바꾸기로 고객이 확인한 기존 점유 예약 ID. 바꾸지 않으면 생략한다.
 *                             지금 점유가 이 예약일 때만 바꾼다 (확인창에 본 것과 다른 점유를 풀지 않도록)
 */
public record ReservationCreateRequest(
        @NotNull Long treatmentId,
        @NotNull OffsetDateTime startsAt,
        Long replaceReservationId) {

    public ReservationCreateRequest(Long treatmentId, OffsetDateTime startsAt) {
        this(treatmentId, startsAt, null);
    }

    public boolean replaces(Long reservationId) {
        return replaceReservationId != null && replaceReservationId.equals(reservationId);
    }
}
