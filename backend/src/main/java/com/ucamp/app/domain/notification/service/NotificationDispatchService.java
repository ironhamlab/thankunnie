package com.ucamp.app.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SmsSendStatus;
import com.ucamp.app.domain.notification.client.TwilioSmsClient;
import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.entity.NotificationDispatchStatus;
import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventStatus;
import com.ucamp.app.domain.notification.entity.NotificationEventType;
import com.ucamp.app.domain.notification.repository.NotificationDispatchRepository;
import com.ucamp.app.domain.notification.repository.NotificationEventRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.VacancySlotRepository;

/**
 * 알림 이벤트를 회원별 발송 대기 작업으로 저장하고, 그 작업을 하나씩 가져간다.
 * 재검사를 통과한 작업은 문자를 보내고 결과를 같은 줄에 남긴다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationDispatchService {

    private static final int FAIL_REASON_MAX_LENGTH = 500;

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationDispatchRepository notificationDispatchRepository;
    private final SubscriptionMatchService subscriptionMatchService;
    private final VacancySlotRepository vacancySlotRepository;
    private final RecipientContactRepository recipientContactRepository;
    private final TwilioSmsClient twilioSmsClient;
    private final Clock clock;

    /**
     * PENDING 이벤트 하나를 고른다. 맞는 회원마다 대기 작업을 넣고 그 이벤트를 PROCESSED로 바꾼다.
     * 같은 이벤트·회원이 이미 있으면 그 회원은 건너뛴다. 한 번 호출에 이벤트 하나만 처리한다.
     *
     * @return 이벤트를 처리했으면 이번에 만든 대기 작업 수. 맞는 회원이 없어도 0이다.
     *         대기 이벤트가 없으면 empty다. 0과 empty는 다르다.
     */
    @Transactional
    public Optional<Integer> createDispatchJobs() {
        // 대기 중인 이벤트 중 가장 먼저 생긴 것 하나만 고른다. ID가 작을수록 먼저 생긴 이벤트다.
        Optional<NotificationEvent> found = notificationEventRepository
                .findFirstByStatusOrderByIdAsc(NotificationEventStatus.PENDING);
        if (found.isEmpty()) {
            return Optional.empty();
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
        return Optional.of(created);
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

    /**
     * 선점한 작업만 다시 본다. 구독이 아직 활성이고, 희망 종료가 지나지 않았고, 빈자리가 아직 팔리면 true.
     * 하나라도 아니면 그 작업을 끝으로 두고 false를 돌려준다. 문자는 보내지 않는다.
     *
     * @param job 선점한 발송 대기 작업
     * @return 아직 보내도 되면 true. 아니거나 선점한 작업이 아니면 false
     */
    @Transactional
    public boolean recheckClaimedJob(NotificationDispatch job) {
        // 대기 중인 작업은 여기서 보지 않는다. 가져온 작업만 다시 확인한다.
        if (job == null || job.getStatus() != NotificationDispatchStatus.PROCESSING) {
            return false;
        }

        NotificationEvent event = notificationEventRepository.findById(job.getEventId()).orElse(null);
        if (event == null) {
            finishWithoutSending(job);
            return false;
        }
        // 활성 구독이고, 희망 종료가 지나지 않았고, 이 빈자리와 아직 맞는 회원인지 다시 본다.
        if (!subscriptionStillMatches(event, job.getUserId())) {
            finishWithoutSending(job);
            return false;
        }
        // 그 사이 예약이 들어왔거나 휴무로 닫히면 더 이상 팔 수 없다.
        if (!slotStillSellable(event)) {
            finishWithoutSending(job);
            return false;
        }
        return true;
    }

    /**
     * 재검사를 통과한 작업에 문자를 보내고 결과를 저장한다.
     * 성공은 SENT, 실패는 FAILED, 응답이 애매하면 UNKNOWN 이다. 실패·불명을 성공으로 바꾸지 않는다.
     *
     * @param job 선점했고 재검사를 통과한 발송 대기 작업
     */
    @Transactional
    public void sendClaimedJob(NotificationDispatch job) {
        if (job == null || job.getStatus() != NotificationDispatchStatus.PROCESSING) {
            return;
        }

        NotificationEvent event = notificationEventRepository.findById(job.getEventId()).orElse(null);
        if (event == null) {
            job.markFailed("알림 이벤트를 찾을 수 없습니다.");
            notificationDispatchRepository.save(job);
            return;
        }

        Optional<String> phone = recipientContactRepository.findPhoneByUserId(job.getUserId());
        if (phone.isEmpty() || !StringUtils.hasText(phone.get())) {
            job.markFailed("회원 전화번호가 없습니다.");
            notificationDispatchRepository.save(job);
            return;
        }

        SmsSendResult result = twilioSmsClient.send(phone.get(), buildMessage(event));
        applySendResult(job, result);
        notificationDispatchRepository.save(job);
        log.info("문자 발송 결과 dispatchId={}, status={}", job.getId(), job.getStatus());
    }

    private String buildMessage(NotificationEvent event) {
        String shopName = recipientContactRepository.findShopNameById(event.getShopId()).orElse("매장");
        return shopName + "에 " + event.getSlotStartsAt() + " 시작 빈자리가 생겼습니다.";
    }

    private void applySendResult(NotificationDispatch job, SmsSendResult result) {
        if (result.status() == SmsSendStatus.SUCCESS) {
            job.markSent();
            return;
        }
        if (result.status() == SmsSendStatus.UNKNOWN) {
            job.markUnknown(trimFailReason(result.failReason()));
            return;
        }
        job.markFailed(trimFailReason(result.failReason()));
    }

    private static String trimFailReason(String failReason) {
        if (!StringUtils.hasText(failReason)) {
            return failReason;
        }
        if (failReason.length() <= FAIL_REASON_MAX_LENGTH) {
            return failReason;
        }
        return failReason.substring(0, FAIL_REASON_MAX_LENGTH);
    }

    // 업장 구독과 카테고리 구독 중 하나라도 아직 맞으면 포함한다. 해제한 구독과 희망 종료가 지난 구독은 빠진다.
    private boolean subscriptionStillMatches(NotificationEvent event, Long userId) {
        List<Long> userIds = subscriptionMatchService.findRecipientUserIds(
                event.getShopId(), event.getSlotStartsAt());
        return userIds.contains(userId);
    }

    // 임박·초임박은 매장이 연 시간이 아직 팔리는지, 다시 열린 자리는 취소된 예약 시간이 아직 팔리는지 본다.
    private boolean slotStillSellable(NotificationEvent event) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (event.getEventType() == NotificationEventType.REOPENED) {
            if (event.getReservationId() == null) {
                return false;
            }
            return vacancySlotRepository.findReopened(event.getReservationId(), now).isPresent();
        }
        return vacancySlotRepository.isOpenSlotSellable(event.getExceptionId(), now);
    }

    // 확인에 걸린 줄은 보내지 않고 끝 상태로 둔다.
    private void finishWithoutSending(NotificationDispatch job) {
        job.markSkipped();
        notificationDispatchRepository.save(job);
    }
}
