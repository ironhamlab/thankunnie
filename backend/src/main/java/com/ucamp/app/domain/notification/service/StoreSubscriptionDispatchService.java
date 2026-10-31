package com.ucamp.app.domain.notification.service;

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
 * 업장 구독에 희망 시간이 생긴 회원에게, 이미 열려 있는 빈자리 발송 줄을 추가한다.
 * 문자는 여기서 보내지 않는다. 줄만 넣어 두면 기존 스케줄러가 보내고, 성공하면 종 알림도 그때 생긴다.
 */
@Service
@RequiredArgsConstructor
public class StoreSubscriptionDispatchService {

    private final ExistingVacancyTargetService existingVacancyTargetService;
    private final NotificationDispatchRepository notificationDispatchRepository;

    /**
     * 이 회원의 발송 줄을 만든다. 업장 구독이 아니거나 희망 시간이 없으면 아무것도 하지 않는다.
     * 같은 이벤트·회원의 줄이 이미 있으면 다시 만들지 않는다.
     *
     * @param subscription 방금 저장한 구독
     */
    @Transactional
    public void enqueue(NotificationSubscription subscription) {
        if (subscription == null || subscription.getSubscriptionType() != SubscriptionType.STORE) {
            return;
        }
        if (subscription.getStartTime() == null || subscription.getEndTime() == null) {
            return;
        }

        List<NotificationEvent> targets = existingVacancyTargetService.findForStore(
                subscription.getShopId(), subscription.getStartTime(), subscription.getEndTime());
        for (NotificationEvent event : targets) {
            // 성공·실패·대기 중 어느 쪽이든 같은 이벤트·회원은 한 줄이다. 다시 만들면 문자가 두 번 나간다.
            if (notificationDispatchRepository.existsByEventIdAndUserId(event.getId(), subscription.getUserId())) {
                continue;
            }
            notificationDispatchRepository.save(NotificationDispatch.builder()
                    .eventId(event.getId())
                    .userId(subscription.getUserId())
                    .build());
        }
    }
}
