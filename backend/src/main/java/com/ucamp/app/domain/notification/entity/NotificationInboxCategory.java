package com.ucamp.app.domain.notification.entity;

/**
 * 받은 알림의 종류. 종 목록 탭과 같은 값이다.
 */
public enum NotificationInboxCategory {

    /** 예약 확정, 예약 취소 */
    RESERVATION,

    /** 빈자리, 임박, 초임박, 카테고리 묶음 */
    INTEREST
}
