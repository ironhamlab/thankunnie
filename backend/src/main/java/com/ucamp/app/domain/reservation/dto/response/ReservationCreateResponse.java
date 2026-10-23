package com.ucamp.app.domain.reservation.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.policy.ReservationStatus;
import com.ucamp.app.domain.reservation.entity.Reservation;

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

    public static ReservationCreateResponse from(Reservation reservation) {
        return new ReservationCreateResponse(
                reservation.getId(),
                reservation.getTreatment().getId(),
                reservation.getStatus(),
                toOffset(reservation.getStartsAt()),
                toOffset(reservation.getEndsAt()),
                reservation.getAppliedPrice(),
                reservation.getDeposit(),
                toOffset(reservation.getHoldExpiresAt()));
    }

    private static OffsetDateTime toOffset(LocalDateTime dateTime) {
        return dateTime.atZone(BookingPolicy.ZONE).toOffsetDateTime();
    }
}
