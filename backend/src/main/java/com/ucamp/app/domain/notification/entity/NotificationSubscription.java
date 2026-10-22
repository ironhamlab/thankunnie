package com.ucamp.app.domain.notification.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.ucamp.app.common.entity.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 구독(찜). 매장 단위 또는 카테고리·지역 단위로 희망 시간대의 빈자리 알림을 받는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "NOTIFICATION_SUBSCRIPTION")
public class NotificationSubscription extends BaseTimeEntity {

    @Id
    @Column(name = "SUBSCRIPTION_ID")
    private Long id;   // ID 생성 방식은 팀 확정 후 적용

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "SUBSCRIPTION_TYPE", nullable = false, length = 20)
    private SubscriptionType subscriptionType;

    @Column(name = "SHOP_ID")
    private Long shopId;

    @Column(name = "CATEGORY_CODE", length = 50)
    private String categoryCode;

    @Column(name = "REGION_CODE", length = 20)
    private String regionCode;

    @Column(name = "START_TIME", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "END_TIME", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private SubscriptionStatus status;

    @Builder
    private NotificationSubscription(Long userId, SubscriptionType subscriptionType, Long shopId,
            String categoryCode, String regionCode, LocalDateTime startTime, LocalDateTime endTime) {
        this.userId = userId;
        this.subscriptionType = subscriptionType;
        this.shopId = shopId;
        this.categoryCode = categoryCode;
        this.regionCode = regionCode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = SubscriptionStatus.ACTIVE;
    }
}
