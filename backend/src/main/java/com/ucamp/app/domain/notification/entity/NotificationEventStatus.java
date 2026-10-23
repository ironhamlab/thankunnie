package com.ucamp.app.domain.notification.entity;

/**
 * 알림 이벤트 처리 상태.
 */
public enum NotificationEventStatus {

    /** 구독 매칭·발송 대기 */
    PENDING,

    /** 매칭·발송 대기열 생성 완료 */
    PROCESSED
}
