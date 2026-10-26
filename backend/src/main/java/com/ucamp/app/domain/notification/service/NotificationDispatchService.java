package com.ucamp.app.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.entity.NotificationDispatchStatus;
import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventStatus;
import com.ucamp.app.domain.notification.repository.NotificationDispatchRepository;
import com.ucamp.app.domain.notification.repository.NotificationEventRepository;

/**
 * 알림 이벤트를 회원별 발송 대기 작업으로 저장하고, 그 작업을 하나씩 가져간다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationDispatchRepository notificationDispatchRepository;
    private final SubscriptionMatchService subscriptionMatchService;
    private final Clock clock;

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

    /**
     * 대기 작업 하나를 처리 중으로 바꾸는 데 성공한 경우에만 그 작업을 돌려준다.
     * 다른 프로세스가 먼저 바꿨으면 수정 건수가 0이라 빈 값을 돌려주고 넘어간다.
     * 이미 처리 중인 작업은 다시 고르지 않는다.
     *
     * @return 이번에 가져간 작업. 대기 작업이 없거나 다른 프로세스가 먼저 가져갔으면 empty
     */
    @Transactional
    public Optional<NotificationDispatch> claimDispatchJob() {
        // 대기 작업 중 가장 먼저 생긴 것 하나만 고른다. 처리 중인 작업은 여기서 빠진다.
        Optional<NotificationDispatch> found = notificationDispatchRepository
                .findFirstByStatusOrderByIdAsc(NotificationDispatchStatus.PENDING);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        Long dispatchId = found.get().getId();
        // 아직 대기일 때만 처리 중으로 바뀐다. 먼저 바뀐 작업은 수정 건수가 0이다.
        int updated = notificationDispatchRepository.claimPending(
                dispatchId,
                NotificationDispatchStatus.PENDING,
                NotificationDispatchStatus.PROCESSING,
                LocalDateTime.now(clock));
        if (updated == 0) {
            return Optional.empty();
        }
        return notificationDispatchRepository.findById(dispatchId);
    }
}
