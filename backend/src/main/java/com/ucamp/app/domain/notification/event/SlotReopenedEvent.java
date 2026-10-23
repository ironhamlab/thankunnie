package com.ucamp.app.domain.notification.event;

/**
 * 취소된 예약 시간을 재공개했음을 알리는 이벤트. 재공개 담당(E)이 즉시 재공개 또는 승인 재공개를 끝낸 뒤 발행한다.
 * <pre>
 * applicationEventPublisher.publishEvent(new SlotReopenedEvent(reservationId));
 * </pre>
 *
 * @param reservationId 취소된 예약 ID
 */
public record SlotReopenedEvent(Long reservationId) {
}
