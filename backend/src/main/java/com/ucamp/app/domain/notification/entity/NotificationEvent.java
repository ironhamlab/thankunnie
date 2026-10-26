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
 * 알림 이벤트. 빈자리(예외 일정 OPEN 행)가 임박·초임박 구간에 들어오거나 취소 후 재공개될 때 한 건 저장한다.
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

    // 숫자 ID는 DB에서 NUMBER(19)다. H2는 이 컬럼을 NUMERIC으로 읽으므로, Long 기본값인 BIGINT로 두면 스키마 검증이 실패한다.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_NOTIFICATION_EVENT_GENERATOR")
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "EVENT_ID")
    private Long id;

    /** 임박(IMMINENT), 초임박(LAST_MINUTE), 취소 재공개(REOPENED). */
    @Enumerated(EnumType.STRING)
    @Column(name = "EVENT_TYPE", nullable = false, length = 20)
    private NotificationEventType eventType;

    /** 빈자리인 예외 일정 OPEN 행 ID. 다른 도메인이 지울 수 있어 외래키는 없다. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "EXCEPTION_ID", nullable = false)
    private Long exceptionId;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SHOP_ID", nullable = false)
    private Long shopId;

    /** 취소 재공개일 때만 취소된 예약 ID. 임박·초임박이면 null. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "RESERVATION_ID")
    private Long reservationId;

    @Column(name = "SLOT_STARTS_AT", nullable = false)
    private LocalDateTime slotStartsAt;

    /** 빈자리 종료 시각. 이 시각은 구간에 포함하지 않는다. */
    @Column(name = "SLOT_ENDS_AT", nullable = false)
    private LocalDateTime slotEndsAt;

    /** 만들 때는 PENDING. 매칭·발송 대기열을 만들면 PROCESSED. */
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
