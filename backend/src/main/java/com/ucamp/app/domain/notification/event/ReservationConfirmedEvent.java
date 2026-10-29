package com.ucamp.app.domain.notification.event;

/**
 * 예약이 결제 후 확정됐음을 알리는 이벤트. ReservationService.confirmReservation 에서 발행한다.
 * 결제 전 점유(HOLD) 단계에서는 발행하지 않는다.
 *
 * @param reservationId 확정된 예약 ID
 */
public record ReservationConfirmedEvent(Long reservationId) {
}
