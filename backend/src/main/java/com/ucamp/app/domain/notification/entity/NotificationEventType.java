package com.ucamp.app.domain.notification.entity;

/**
 * 알림 이벤트 유형.
 */
public enum NotificationEventType {

    /** 빈자리가 임박 구간(시작 8시간 전)에 들어옴 */
    IMMINENT,

    /** 빈자리가 초임박 구간(시작 3시간 전)에 들어옴 */
    LAST_MINUTE,

    /** 취소된 예약 시간이 다시 판매 가능해짐 */
    REOPENED
}
