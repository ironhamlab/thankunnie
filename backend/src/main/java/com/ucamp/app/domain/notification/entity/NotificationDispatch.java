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
 * 알림 대기 줄 한 장.
 * 빈자리 소식 하나와 받을 회원 한 명을 한 줄로 적는다.
 * 문자 발송 결과도 이 줄에 남긴다. 새 이력 테이블은 두지 않는다.
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

    /**
     * 만들 때는 PENDING. 선점에 성공하면 PROCESSING.
     * 재검사에 걸리면 SKIPPED. 문자 결과는 SENT, FAILED, UNKNOWN.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private NotificationDispatchStatus status;

    /** 실패·결과 불명일 때만 채운다. 그 외에는 null. */
    @Column(name = "FAIL_REASON", length = 500)
    private String failReason;

    @Builder
    private NotificationDispatch(Long eventId, Long userId) {
        this.eventId = eventId;
        this.userId = userId;
        this.status = NotificationDispatchStatus.PENDING;
    }

    /** 발송 직전 재검사에 걸리면 보내지 않고 끝낸다. */
    public void markSkipped() {
        this.status = NotificationDispatchStatus.SKIPPED;
        this.failReason = null;
    }

    /** 문자 발송이 끝났다. */
    public void markSent() {
        this.status = NotificationDispatchStatus.SENT;
        this.failReason = null;
    }

    /**
     * 문자 발송이 실패했다.
     *
     * @param failReason 오류 사유. 없으면 null
     */
    public void markFailed(String failReason) {
        this.status = NotificationDispatchStatus.FAILED;
        this.failReason = failReason;
    }

    /**
     * 문자 API 응답이 애매해 발송 여부를 다시 봐야 한다.
     * 성공으로 바꾸지 않는다.
     *
     * @param failReason 확인이 필요한 이유. 없으면 null
     */
    public void markUnknown(String failReason) {
        this.status = NotificationDispatchStatus.UNKNOWN;
        this.failReason = failReason;
    }
}
