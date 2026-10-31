package com.ucamp.app.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 이 매장에서 이미 만든 알림 중, 빈자리 시작이 희망 시간 안인 것을 시작 시각 순으로 가져온다.
     * 희망 시작·종료와 같은 시각도 포함한다. 아직 이벤트가 없는 먼 빈자리는 여기 없다.
     *
     * @param shopId    매장 ID
     * @param hopeStart 희망 시작
     * @param hopeEnd   희망 종료
     * @return 희망 시간 안의 알림 이벤트
     */
    @Query("""
            select e from NotificationEvent e
            where e.shopId = :shopId
              and e.slotStartsAt >= :hopeStart
              and e.slotStartsAt <= :hopeEnd
            order by e.slotStartsAt asc, e.id asc
            """)
    List<NotificationEvent> findByShopAndSlotStartInHopeTime(
            @Param("shopId") Long shopId,
            @Param("hopeStart") LocalDateTime hopeStart,
            @Param("hopeEnd") LocalDateTime hopeEnd);
}
