package com.ucamp.app.domain.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionType;
import com.ucamp.app.domain.notification.repository.NotificationDispatchRepository;

/**
 * 카테고리·지역 구독을 방금 저장한 회원에게, 이미 열려 있는 빈자리를 넣는다.
 * 업장 구독과 같이 이벤트가 없는 먼 빈자리는 넣지 않는다.
 * 여러 건이면 기존 묶음과 같이 첫 건만 발송 줄로 만들고, 그때부터 60분 안의 나머지는 묶음 메모장에 둔다.
 * 문자는 여기서 보내지 않는다. 발송 줄은 스케줄러가 보내고, 묶음은 60분이 지난 뒤 스케줄러가 보낸다.
 */
@Service
@RequiredArgsConstructor
public class CategoryRegionSubscriptionDispatchService {

    /** 카테고리·지역 단건 문자를 만든 뒤, 이 시간 동안은 다음 빈자리를 발송 줄에 넣지 않는다. */
    private static final Duration COOLDOWN = Duration.ofMinutes(60);

    private final ExistingVacancyTargetService existingVacancyTargetService;
    private final NotificationDispatchRepository notificationDispatchRepository;
    private final CategoryRegionPendingQueue categoryRegionPendingQueue;
    private final Clock clock;

    /**
     * 이 구독의 발송 줄 또는 묶음 메모장을 채운다.
     * 카테고리·지역 구독이 아니거나 희망 시간이 없으면 아무것도 하지 않는다.
     * 같은 이벤트·회원의 줄이 이미 있으면 다시 만들지 않는다.
     *
     * @param subscription 방금 저장한 구독. 묶음 메모장 키로 구독 ID가 필요하다
     */
    @Transactional
    public void enqueue(NotificationSubscription subscription) {
        if (subscription == null || subscription.getId() == null) {
            return;
        }
        if (subscription.getSubscriptionType() != SubscriptionType.CATEGORY_REGION) {
            return;
        }
        if (subscription.getStartTime() == null || subscription.getEndTime() == null) {
            return;
        }

        List<NotificationEvent> targets = existingVacancyTargetService.findForCategoryRegion(
                subscription.getCategoryId(), subscription.getRegionCode(),
                subscription.getStartTime(), subscription.getEndTime());
        LocalDateTime now = LocalDateTime.now(clock);
        for (NotificationEvent event : targets) {
            // 이미 이 회원에게 발송 줄이 있으면 단건이든 묶음이든 다시 넣지 않는다.
            if (notificationDispatchRepository.existsByEventIdAndUserId(event.getId(), subscription.getUserId())) {
                continue;
            }
            if (isCoolingDown(subscription.getId(), now)) {
                categoryRegionPendingQueue.addPendingEvent(subscription.getId(), event.getId());
                continue;
            }
            notificationDispatchRepository.save(NotificationDispatch.builder()
                    .eventId(event.getId())
                    .userId(subscription.getUserId())
                    .build());
            categoryRegionPendingQueue.markSent(subscription.getId(), now);
        }
    }

    // 마지막 발송 시각부터 60분이 아직 안 지났으면 true. 한 번도 안 보냈으면 false.
    private boolean isCoolingDown(Long subscriptionId, LocalDateTime now) {
        LocalDateTime lastSentAt = categoryRegionPendingQueue.lastSentAt(subscriptionId);
        if (lastSentAt == null) {
            return false;
        }
        return now.isBefore(lastSentAt.plus(COOLDOWN));
    }
}
