package com.ucamp.app.domain.reservation.entity;

/**
 * 내 예약 목록을 시술 종료 시각으로 나누는 기준. 시술 중인 예약은 아직 끝나지 않았으므로 UPCOMING 이다.
 */
public enum ReservationTiming {
    /** 시술 종료 시각 전 */
    UPCOMING,
    /** 시술 종료 시각이 지남 */
    PAST
}
