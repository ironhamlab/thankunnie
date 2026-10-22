package com.ucamp.app.domain.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.notification.entity.NotificationSubscription;

/**
 * 알림 구독 저장소.
 */
public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {
}
