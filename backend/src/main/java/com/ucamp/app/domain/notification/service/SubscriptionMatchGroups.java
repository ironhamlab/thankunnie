package com.ucamp.app.domain.notification.service;

import java.util.List;

import com.ucamp.app.domain.notification.entity.NotificationSubscription;

/**
 * 한 빈자리를 누구에게 알릴지 둘로 나눈 결과.
 * 매장 알림으로 이 빈자리 알림을 받을 회원은 카테고리 목록에 넣지 않는다.
 *
 * @param storeUserIds 매장 알림으로 이 빈자리 알림을 받을 회원 ID. 한 회원은 한 번만 들어 있다
 * @param categoryRegionOnlySubscriptions 카테고리·지역 알림으로만 이 빈자리를 받을 알림.
 *                                        매장 알림으로도 받을 회원의 알림은 없다
 */
public record SubscriptionMatchGroups(
        List<Long> storeUserIds,
        List<NotificationSubscription> categoryRegionOnlySubscriptions) {
}
