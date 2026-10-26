package com.ucamp.app.domain.notification.entity;

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
 * 발송 대기 작업. 알림 이벤트 하나와 회원 하나를 한 줄로 저장한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "NOTIFICATION_DISPATCH")
@SequenceGenerator(
        name = "SEQ_NOTIFICATION_DISPATCH_GENERATOR",
        sequenceName = "SEQ_NOTIFICATION_DISPATCH",
        allocationSize = 1)
public class NotificationDispatch extends BaseTimeEntity {

    // H2는 NUMBER(19)를 NUMERIC으로 읽는다. BIGINT로 두면 스키마 검증이 실패한다.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_NOTIFICATION_DISPATCH_GENERATOR")
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "DISPATCH_ID")
    private Long id;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "EVENT_ID", nullable = false)
    private Long eventId;

    /** 알림을 받을 회원. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 만들 때는 PENDING. 처리 중·끝으로 바꾸는 일은 다음 단계다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private NotificationDispatchStatus status;

    @Builder
    private NotificationDispatch(Long eventId, Long userId) {
        this.eventId = eventId;
        this.userId = userId;
        this.status = NotificationDispatchStatus.PENDING;
    }
}
