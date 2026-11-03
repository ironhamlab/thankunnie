package com.ucamp.app.domain.reservation.dto.response;

/**
 * 예약 대기열에서 본 내 상태.
 */
public enum ReservationQueueStatus {
    /** 앞사람이 있거나 앞사람이 결제 중이라 기다린다 */
    WAITING,
    /** 내 차례다. turnExpiresAt 전에 예약(점유)을 만들어야 한다 */
    MY_TURN,
    /** 내가 이 빈자리를 점유하고 있다. 결제 화면으로 가면 된다 */
    HOLDING,
    /** 예약이 확정됐거나 마감·닫힘으로 더 기다려도 예약할 수 없다 */
    CLOSED
}
