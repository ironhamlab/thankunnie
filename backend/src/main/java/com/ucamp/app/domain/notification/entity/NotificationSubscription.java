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
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림(찜). 매장 단위 또는 카테고리·지역 단위로 희망 시간대의 빈자리 알림을 받는다.
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

    // NUMBER(19) 는 H2 에서도 NUMERIC 으로 읽히므로 스키마 검증이 BIGINT 를 기대하지 않게 맞춘다
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_NOTIFICATION_SUBSCRIPTION_GENERATOR")
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SUBSCRIPTION_ID")
    private Long id;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "SUBSCRIPTION_TYPE", nullable = false, length = 20)
    private SubscriptionType subscriptionType;

    /** 업장 알림(STORE)일 때만 넣는다. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SHOP_ID")
    private Long shopId;

    /** 업장 알림(STORE)일 때만 넣는다. 고객이 고른 시술이다. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "TREATMENT_ID")
    private Long treatmentId;

    /** 카테고리 알림(CATEGORY_REGION)일 때만 넣는다. */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "CATEGORY_ID")
    private Long categoryId;

    /** 카테고리 알림(CATEGORY_REGION)일 때만 넣는다. */
    @Column(name = "REGION_CODE", length = 20)
    private String regionCode;

    /** 업장 찜은 비울 수 있다. 비우면 알림을 보내지 않는다. */
    @Column(name = "START_TIME")
    private LocalDateTime startTime;

    /** 업장 찜은 비울 수 있다. 시작과 함께 비우거나 함께 넣는다. */
    @Column(name = "END_TIME")
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private SubscriptionStatus status;

    @Builder
    private NotificationSubscription(Long userId, SubscriptionType subscriptionType, Long shopId, Long treatmentId,
            Long categoryId, String regionCode, LocalDateTime startTime, LocalDateTime endTime) {
        validateTarget(subscriptionType, shopId, treatmentId, categoryId, regionCode);
        validateTime(subscriptionType, startTime, endTime);
        requireTreatmentWhenNotifying(subscriptionType, treatmentId, startTime, endTime);
        this.userId = userId;
        this.subscriptionType = subscriptionType;
        this.shopId = shopId;
        this.treatmentId = treatmentId;
        this.categoryId = categoryId;
        this.regionCode = regionCode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = SubscriptionStatus.ACTIVE;
    }

    /**
     * 알림 대상과 희망 시간을 변경한다. 알림 유형은 바꿀 수 없으며, 기존 유형에 맞는 대상만 허용한다.
     *
     * @param shopId      매장 ID. 카테고리 알림이면 null
     * @param treatmentId 시술 ID. 업장 알림이면 고객이 고른 시술, 카테고리 알림이면 null
     * @param categoryId  카테고리 ID. 업장 알림이면 null
     * @param regionCode  지역코드. 업장 알림이면 null
     * @param startTime   알림 희망 시작 시각
     * @param endTime     알림 희망 종료 시각
     */
    public void changeCondition(Long shopId, Long treatmentId, Long categoryId, String regionCode,
            LocalDateTime startTime, LocalDateTime endTime) {
        validateTarget(subscriptionType, shopId, treatmentId, categoryId, regionCode);
        validateTime(subscriptionType, startTime, endTime);
        requireTreatmentWhenNotifying(subscriptionType, treatmentId, startTime, endTime);
        this.shopId = shopId;
        this.treatmentId = treatmentId;
        this.categoryId = categoryId;
        this.regionCode = regionCode;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /** 알림을 해제한다. 알림 대상에서 제외된다. */
    public void deactivate() {
        this.status = SubscriptionStatus.INACTIVE;
    }

    /**
     * 해제하지 않은 알림인지 확인한다.
     *
     * @return 활성 알림이면 true
     */
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE;
    }

    /**
     * 알림 유형과 대상이 맞는지 확인한다.
     * 업장 알림은 매장 ID만 필수다. 시술은 알림을 켤 때 따로 확인한다.
     * 카테고리 알림은 카테고리 ID와 지역코드만 허용한다.
     */
    private static void validateTarget(SubscriptionType subscriptionType, Long shopId, Long treatmentId,
            Long categoryId, String regionCode) {
        switch (subscriptionType) {
            case STORE -> {
                if (shopId == null || categoryId != null || regionCode != null) {
                    throw new BusinessException(ErrorCode.SUBSCRIPTION_INVALID_TARGET);
                }
            }
            case CATEGORY_REGION -> {
                if (shopId != null || treatmentId != null || categoryId == null || regionCode == null
                        || regionCode.isBlank()) {
                    throw new BusinessException(ErrorCode.SUBSCRIPTION_INVALID_TARGET);
                }
            }
        }
    }

    /**
     * 업장 찜은 희망 시간을 둘 다 비울 수 있다. 비우면 알림 대상이 아니다.
     * 시간을 넣으면 시작이 종료보다 앞이어야 한다. 하나만 비우거나 카테고리 알림의 빈 시간은 거절한다.
     */
    private static void validateTime(SubscriptionType subscriptionType, LocalDateTime startTime,
            LocalDateTime endTime) {
        if (startTime == null && endTime == null) {
            if (subscriptionType != SubscriptionType.STORE) {
                throw new BusinessException(ErrorCode.SUBSCRIPTION_INVALID_TIME);
            }
            return;
        }
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_INVALID_TIME);
        }
    }

    // 희망 시간을 넣는 업장 알림은 시술이 있어야 한다. 시간 없는 찜은 시술을 비워 둘 수 있다.
    private static void requireTreatmentWhenNotifying(SubscriptionType subscriptionType, Long treatmentId,
            LocalDateTime startTime, LocalDateTime endTime) {
        if (subscriptionType != SubscriptionType.STORE) {
            return;
        }
        if (startTime != null && endTime != null && treatmentId == null) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_INVALID_TARGET);
        }
    }
}
