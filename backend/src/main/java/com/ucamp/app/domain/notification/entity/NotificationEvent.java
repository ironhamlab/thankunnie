package com.ucamp.app.domain.notification.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ucamp.app.common.entity.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 이벤트. 빈자리(예외 일정 OPEN 행)가 임박 구간에 들어오거나 취소 후 재공개될 때 한 건 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "NOTIFICATION_EVENT")
@SequenceGenerator(
        name = "SEQ_NOTIFICATION_EVENT_GENERATOR",
        sequenceName = "SEQ_NOTIFICATION_EVENT",
        allocationSize = 1)
public class NotificationEvent extends BaseTimeEntity {

    // NUMBER(19) 는 H2 에서도 NUMERIC 으로 읽히므로 스키마 검증이 BIGINT 를 기대하지 않게 맞춘다
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_NOTIFICATION_EVENT_GENERATOR")
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "EVENT_ID")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "EVENT_TYPE", nullable = false, length = 20)
    private NotificationEventType eventType;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "EXCEPTION_ID", nullable = false)
    private Long exceptionId;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SHOP_ID", nullable = false)
    private Long shopId;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "RESERVATION_ID")
    private Long reservationId;

    @Column(name = "SLOT_STARTS_AT", nullable = false)
    private LocalDateTime slotStartsAt;

    @Column(name = "SLOT_ENDS_AT", nullable = false)
    private LocalDateTime slotEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private NotificationEventStatus status;

    @Builder
    private NotificationEvent(NotificationEventType eventType, Long exceptionId, Long shopId, Long reservationId,
            LocalDateTime slotStartsAt, LocalDateTime slotEndsAt) {
        this.eventType = eventType;
        this.exceptionId = exceptionId;
        this.shopId = shopId;
        this.reservationId = reservationId;
        this.slotStartsAt = slotStartsAt;
        this.slotEndsAt = slotEndsAt;
        this.status = NotificationEventStatus.PENDING;
    }
}
