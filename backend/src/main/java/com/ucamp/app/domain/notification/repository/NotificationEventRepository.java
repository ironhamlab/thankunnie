package com.ucamp.app.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventStatus;
import com.ucamp.app.domain.notification.entity.NotificationEventType;

/**
 * 알림 이벤트 저장소.
 */
public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    /**
     * 같은 빈자리·유형·시작 시각의 이벤트가 이미 있는지 확인한다.
     *
     * @param exceptionId  빈자리인 OPEN 예외 일정 ID
     * @param eventType    이벤트 유형
     * @param slotStartsAt 빈자리 시작 시각
     * @return 이미 있으면 true
     */
    boolean existsByExceptionIdAndEventTypeAndSlotStartsAt(Long exceptionId, NotificationEventType eventType,
            LocalDateTime slotStartsAt);

    /**
     * 해당 상태의 이벤트 중 ID가 가장 작은 하나를 조회한다.
     *
     * @param status 이벤트 상태
     * @return 있으면 그 이벤트. 없으면 empty
     */
    Optional<NotificationEvent> findFirstByStatusOrderByIdAsc(NotificationEventStatus status);
}
