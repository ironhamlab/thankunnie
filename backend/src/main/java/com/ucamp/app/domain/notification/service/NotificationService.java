package com.ucamp.app.domain.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;

/**
 * 알림 업무 로직.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationSubscriptionRepository notificationSubscriptionRepository;

    public NotificationService(NotificationSubscriptionRepository notificationSubscriptionRepository) {
        this.notificationSubscriptionRepository = notificationSubscriptionRepository;
    }
}
