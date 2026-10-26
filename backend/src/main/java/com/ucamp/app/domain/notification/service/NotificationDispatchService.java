package com.ucamp.app.domain.notification.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventStatus;
import com.ucamp.app.domain.notification.repository.NotificationDispatchRepository;
import com.ucamp.app.domain.notification.repository.NotificationEventRepository;

/**
 * 알림 이벤트를 회원별 발송 대기 작업으로 저장한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationDispatchRepository notificationDispatchRepository;
    private final SubscriptionMatchService subscriptionMatchService;

    /**
     * PENDING 이벤트 하나를 고른다. 맞는 회원마다 대기 작업을 넣고 그 이벤트를 PROCESSED로 바꾼다.
     * 같은 이벤트·회원이 이미 있으면 그 회원은 건너뛴다. 한 번 호출에 이벤트 하나만 처리한다.
     *
     * @return 이번에 새로 만든 대기 작업 수. PENDING 이벤트가 없거나 새로 넣을 회원이 없으면 0
     */
    @Transactional
    public int createDispatchJobs() {
        // 대기 중인 이벤트 중 가장 먼저 생긴 것 하나만 고른다. ID가 작을수록 먼저 생긴 이벤트다.
        Optional<NotificationEvent> found = notificationEventRepository
                .findFirstByStatusOrderByIdAsc(NotificationEventStatus.PENDING);
        if (found.isEmpty()) {
            return 0;
        }

        NotificationEvent event = found.get();
        // 이 빈자리를 받을 회원. 업장 구독과 카테고리 구독이 겹쳐도 회원은 한 명만 나온다.
        List<Long> userIds = subscriptionMatchService.findRecipientUserIds(
                event.getShopId(), event.getSlotStartsAt());

        int created = 0;
        for (Long userId : userIds) {
            // 같은 이벤트·회원이 이미 있으면 건너뛴다. 그 조합은 DB에서도 한 줄만 허용한다.
            if (notificationDispatchRepository.existsByEventIdAndUserId(event.getId(), userId)) {
                continue;
            }
            notificationDispatchRepository.save(NotificationDispatch.builder()
                    .eventId(event.getId())
                    .userId(userId)
                    .build());
            created++;
        }

        // 받을 회원이 없어도 이 이벤트 매칭은 끝이다. PENDING으로 두면 다음에 또 집는다.
        event.markProcessed();
        return created;
    }
}
