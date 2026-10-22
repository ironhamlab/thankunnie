package com.ucamp.app.domain.notification.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionStatus;
import com.ucamp.app.domain.notification.entity.SubscriptionType;

/**
 * 알림 구독 응답.
 *
 * @param subscriptionId   구독 ID
 * @param subscriptionType 구독 유형
 * @param shopId           매장 ID, 카테고리·지역 구독이면 null
 * @param categoryCode     카테고리 코드, 업장 구독이면 null
 * @param regionCode       지역 코드, 업장 구독이면 null
 * @param startTime        알림 희망 시작 시각
 * @param endTime          알림 희망 종료 시각
 * @param status           구독 상태
 */
public record NotificationSubscriptionResponse(
        Long subscriptionId,
        SubscriptionType subscriptionType,
        Long shopId,
        String categoryCode,
        String regionCode,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        SubscriptionStatus status) {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    public static NotificationSubscriptionResponse from(NotificationSubscription subscription) {
        return new NotificationSubscriptionResponse(
                subscription.getId(),
                subscription.getSubscriptionType(),
                subscription.getShopId(),
                subscription.getCategoryCode(),
                subscription.getRegionCode(),
                toOffsetTime(subscription.getStartTime()),
                toOffsetTime(subscription.getEndTime()),
                subscription.getStatus());
    }

    private static OffsetDateTime toOffsetTime(LocalDateTime time) {
        return time.atZone(ZONE_SEOUL).toOffsetDateTime();
    }
}
