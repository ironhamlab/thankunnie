package com.ucamp.app.domain.reservation.entity;

/**
 * 예약 상태. 예약은 고객이 예약 클릭(점유)할 때 HOLD 로 생성된다.
 * 점유 만료는 상태를 바로 바꾸지 않고 임시확보만료일시로 판단한다.
 */
public enum ReservationStatus {

    /** 점유: 예약 클릭 후 결제 대기 (5분) */
    HOLD,
    /** 예약 완료: 결제 확정 */
    CONFIRMED,
    /** 취소 처리 중: 환불 완료 전까지 시간을 계속 잡고 있다 */
    CANCEL_PENDING,
    /** 취소 완료 */
    CANCELLED,
    /** 점유 만료: 5분 안에 결제하지 않음 */
    EXPIRED
}
