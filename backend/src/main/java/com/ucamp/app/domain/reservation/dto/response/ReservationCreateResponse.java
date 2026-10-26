package com.ucamp.app.domain.reservation.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 생성된 예약(점유). 고객은 holdExpiresAt 전까지 예약금(deposit)을 결제해야 한다.
 *
 * @param reservationId 예약 ID
 * @param treatmentId   시술 ID
 * @param status        예약 상태 (생성 시 HOLD)
 * @param startsAt      시작 일시
 * @param endsAt        종료 일시 (미포함)
 * @param appliedPrice  클릭 시점 적용 판매가
 * @param deposit       예약금
 * @param holdExpiresAt 점유 만료 일시
 */
public record ReservationCreateResponse(
        Long reservationId,
        Long treatmentId,
        ReservationStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        int appliedPrice,
        int deposit,
        OffsetDateTime holdExpiresAt) {

    /**
     * 저장된 예약으로 응답을 만든다. 시각은 한국 시간대(+09:00)로 바꾼다.
     *
     * @param reservation 저장된 예약
     * @return 예약 생성 응답
     */
    public static ReservationCreateResponse from(Reservation reservation) {
        return new ReservationCreateResponse(
                reservation.getId(),
                reservation.getTreatmentId(),
                reservation.getStatus(),
                TimeUtils.toOffset(reservation.getStartsAt()),
                TimeUtils.toOffset(reservation.getEndsAt()),
                reservation.getAppliedPrice(),
                reservation.getDeposit(),
                TimeUtils.toOffset(reservation.getHoldExpiresAt()));
    }
}
