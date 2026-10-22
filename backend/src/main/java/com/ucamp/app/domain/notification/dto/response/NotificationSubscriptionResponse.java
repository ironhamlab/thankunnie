package com.ucamp.app.domain.notification.dto.response;

import com.ucamp.app.domain.notification.entity.NotificationSubscription;

/**
 * 알림 구독 응답.
 *
 * @param subscriptionId 구독 식별자
 */
public record NotificationSubscriptionResponse(Long subscriptionId) {

    public static NotificationSubscriptionResponse from(NotificationSubscription subscription) {
        return new NotificationSubscriptionResponse(subscription.getId());
    }
}
