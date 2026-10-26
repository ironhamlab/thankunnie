package com.ucamp.app.domain.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.notification.entity.NotificationDispatch;

/**
 * 발송 대기 작업 저장소.
 */
public interface NotificationDispatchRepository extends JpaRepository<NotificationDispatch, Long> {

    /**
     * 같은 이벤트·회원의 대기 작업이 이미 있는지 확인한다.
     *
     * @param eventId 알림 이벤트 ID
     * @param userId  회원 ID
     * @return 이미 있으면 true
     */
    boolean existsByEventIdAndUserId(Long eventId, Long userId);
}
