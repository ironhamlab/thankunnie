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
 * 받은 알림 한 줄.
 * 고객 알림은 회원 ID 만, 사장님 알림은 매장 ID 만 채운다.
 * 읽지 않았으면 readAt 이 null 이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "NOTIFICATION_INBOX")
@SequenceGenerator(
        name = "SEQ_NOTIFICATION_INBOX_GENERATOR",
        sequenceName = "SEQ_NOTIFICATION_INBOX",
        allocationSize = 1)
public class NotificationInbox extends BaseTimeEntity {

    // H2 는 NUMBER(19) 를 NUMERIC 으로 읽는다. BIGINT 로 두면 스키마 검증이 실패한다.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_NOTIFICATION_INBOX_GENERATOR")
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "INBOX_ID")
    private Long id;

    /** 알림을 받은 회원. 사장님 알림이면 null. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "USER_ID")
    private Long userId;

    /** 알림을 받은 매장. 고객 알림이면 null. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SHOP_ID")
    private Long shopId;

    /** 예약(RESERVATION) 또는 관심(INTEREST). */
    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false, length = 20)
    private NotificationInboxCategory category;

    /** 종 목록에 보이는 제목. */
    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    /** 종 목록에 보이는 본문. */
    @Column(name = "BODY", nullable = false, length = 500)
    private String body;

    /** 누르면 이동할 화면 경로. 이동할 곳이 없으면 null. */
    @Column(name = "LINK_PATH", length = 200)
    private String linkPath;

    /** 읽은 시각. null 이면 아직 읽지 않은 알림이다. */
    @Column(name = "READ_AT")
    private LocalDateTime readAt;

    /**
     * 회원 알림이면 userId 만, 매장 알림이면 shopId 만 넣는다.
     * 둘 다 있거나 둘 다 없으면 테이블 체크 제약과 같이 거절한다.
     */
    @Builder
    private NotificationInbox(Long userId, Long shopId, NotificationInboxCategory category, String title, String body,
            String linkPath) {
        if (userId == null && shopId == null) {
            throw new IllegalArgumentException("받은 알림은 회원 또는 매장 중 하나는 있어야 한다");
        }
        if (userId != null && shopId != null) {
            throw new IllegalArgumentException("받은 알림은 회원과 매장을 같이 지정할 수 없다");
        }
        this.userId = userId;
        this.shopId = shopId;
        this.category = category;
        this.title = title;
        this.body = body;
        this.linkPath = linkPath;
    }

    /**
     * 아직 읽지 않았을 때만 읽은 시각을 적는다.
     * 이미 읽은 알림은 처음 읽은 시각을 유지한다.
     *
     * @param readAt 읽은 시각. null 이면 바꾸지 않는다
     */
    public void markRead(LocalDateTime readAt) {
        if (readAt == null || this.readAt != null) {
            return;
        }
        this.readAt = readAt;
    }
}
