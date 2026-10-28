package com.ucamp.app.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventStatus;
import com.ucamp.app.domain.notification.entity.NotificationEventType;

/**
 * 알림 소식을 저장하고 찾는 곳.
 * 같은 매장·같은 종류·같은 시작 시각은 한 번만 쌓였는지 확인한다.
 */
public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    /**
     * 같은 매장, 같은 종류, 같은 시작 시각의 소식이 이미 있는지 본다.
     * OPEN 행이 없는 재공개도 이 기준으로 중복을 막는다.
     *
     * @param shopId       매장 번호
     * @param eventType    임박, 초임박, 다시 열림 중 무엇인지
     * @param slotStartsAt 빈자리 시작 시각
     * @return 이미 있으면 true
     */
    boolean existsByShopIdAndEventTypeAndSlotStartsAt(Long shopId, NotificationEventType eventType,
            LocalDateTime slotStartsAt);

    /**
     * 해당 상태의 이벤트 중 ID가 가장 작은 하나를 조회한다.
     *
     * @param status 아직 받을 사람을 고르기 전인지, 고른 뒤인지
     * @return 있으면 그 소식. 없으면 빈 값
     */
    Optional<NotificationEvent> findFirstByStatusOrderByIdAsc(NotificationEventStatus status);
}
