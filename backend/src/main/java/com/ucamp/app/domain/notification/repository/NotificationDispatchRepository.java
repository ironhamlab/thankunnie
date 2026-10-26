package com.ucamp.app.domain.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.notification.entity.NotificationDispatch;

/**
 * 발송 대기 작업 저장소.
 */
public interface NotificationDispatchRepository extends JpaRepository<NotificationDispatch, Long> {
}
