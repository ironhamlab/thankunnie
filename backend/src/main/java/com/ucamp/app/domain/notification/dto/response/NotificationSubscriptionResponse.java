package com.ucamp.app.domain.notification.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionStatus;
import com.ucamp.app.domain.notification.entity.SubscriptionType;

/**
 * 알림 등록·조회·수정 응답.
 *
 * @param subscriptionId   알림 ID
 * @param subscriptionType 알림 유형
 * @param shopId           매장 ID. 카테고리 알림이면 null
 * @param treatmentId      시술 ID. 업장 알림이면 고객이 고른 시술, 카테고리 알림이면 null
 * @param categoryId       카테고리 ID. 업장 알림이면 null
 * @param regionCode       지역코드. 업장 알림이면 null
 * @param startTime        알림 희망 시작 시각
 * @param endTime          알림 희망 종료 시각
 * @param status           알림 상태
 */
public record NotificationSubscriptionResponse(
        Long subscriptionId,
        SubscriptionType subscriptionType,
        Long shopId,
        Long treatmentId,
        Long categoryId,
        String regionCode,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        SubscriptionStatus status) {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * 알림 엔티티를 응답으로 변환한다.
     *
     * @param subscription 알림
     * @return 알림 응답
     */
    public static NotificationSubscriptionResponse from(NotificationSubscription subscription) {
        return new NotificationSubscriptionResponse(
                subscription.getId(),
                subscription.getSubscriptionType(),
                subscription.getShopId(),
                subscription.getTreatmentId(),
                subscription.getCategoryId(),
                subscription.getRegionCode(),
                toOffsetTime(subscription.getStartTime()),
                toOffsetTime(subscription.getEndTime()),
                subscription.getStatus());
    }

    private static OffsetDateTime toOffsetTime(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZONE_SEOUL).toOffsetDateTime();
    }
}
