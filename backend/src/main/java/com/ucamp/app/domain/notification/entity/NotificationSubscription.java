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

import com.ucamp.app.common.entity.BaseTimeEntity;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

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
@SequenceGenerator(
        name = "SEQ_NOTIFICATION_SUBSCRIPTION_GENERATOR",
        sequenceName = "SEQ_NOTIFICATION_SUBSCRIPTION",
        allocationSize = 1)
public class NotificationSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_NOTIFICATION_SUBSCRIPTION_GENERATOR")
    @Column(name = "SUBSCRIPTION_ID")
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "SUBSCRIPTION_TYPE", nullable = false, length = 20)
    private SubscriptionType subscriptionType;

    @Column(name = "SHOP_ID")
    private Long shopId;

    @Column(name = "CATEGORY_ID")
    private Long categoryId;

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
            Long categoryId, String regionCode, LocalDateTime startTime, LocalDateTime endTime) {
        validateTarget(subscriptionType, shopId, categoryId, regionCode);
        validateTime(startTime, endTime);
        this.userId = userId;
        this.subscriptionType = subscriptionType;
        this.shopId = shopId;
        this.categoryId = categoryId;
        this.regionCode = regionCode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = SubscriptionStatus.ACTIVE;
    }

    /**
     * 구독 대상과 희망 시간을 변경한다. 구독 유형은 바꿀 수 없으며, 기존 유형에 맞는 대상만 허용한다.
     *
     * @param shopId     매장 ID. 카테고리 구독이면 null
     * @param categoryId 카테고리 ID. 업장 구독이면 null
     * @param regionCode 지역코드. 업장 구독이면 null
     * @param startTime  알림 희망 시작 시각
     * @param endTime    알림 희망 종료 시각
     */
    public void changeCondition(Long shopId, Long categoryId, String regionCode,
            LocalDateTime startTime, LocalDateTime endTime) {
        validateTarget(subscriptionType, shopId, categoryId, regionCode);
        validateTime(startTime, endTime);
        this.shopId = shopId;
        this.categoryId = categoryId;
        this.regionCode = regionCode;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /** 구독을 해제한다. 알림 대상에서 제외된다. */
    public void deactivate() {
        this.status = SubscriptionStatus.INACTIVE;
    }

    /**
     * 해제하지 않은 구독인지 확인한다.
     *
     * @return 활성 구독이면 true
     */
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }

    // DB 의 CK_SUBSCRIPTION_TARGET 과 같은 조건. DB 제약 위반(500) 전에 업무 예외(400)로 거절한다
    private static void validateTarget(SubscriptionType subscriptionType, Long shopId,
            Long categoryId, String regionCode) {
        boolean valid = switch (subscriptionType) {
            case STORE -> shopId != null && categoryId == null && regionCode == null;
            case CATEGORY_REGION -> shopId == null && categoryId != null && hasText(regionCode);
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_INVALID_TARGET);
        }
    }

    private static void validateTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_INVALID_TIME);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
