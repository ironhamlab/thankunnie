package com.ucamp.app.domain.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SmsSendStatus;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;
import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.entity.NotificationDispatchStatus;
import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.entity.NotificationEventStatus;
import com.ucamp.app.domain.notification.entity.NotificationEventType;
import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.repository.NotificationDispatchRepository;
import com.ucamp.app.domain.notification.repository.NotificationEventRepository;
import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.VacancySlotRepository;
import com.ucamp.app.domain.vacancy.repository.ShopStatusQueryRepository;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

/**
 * 알림 이벤트를 회원별 발송 대기 작업으로 저장하고, 그 작업을 하나씩 가져간다.
 * 재검사를 통과한 작업은 문자를 보내고 결과를 같은 줄에 남긴다.
 * 문자 발송에 성공하면 받은 알림에 한 줄 남긴다. 실패·결과 불명·생략은 남기지 않는다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationDispatchService {

    private static final int FAIL_REASON_MAX_LENGTH = 500;

    /** 문자 본문에 넣는 빈자리 시작 시각. 연도를 포함해 다른 해 예약과 구분한다. */
    private static final DateTimeFormatter SLOT_START_TEXT =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm");

    /** 이미 끝난 발송. 이 상태면 문자를 다시 보내지 않는다. */
    private static final Set<NotificationDispatchStatus> DO_NOT_RESEND = EnumSet.of(
            NotificationDispatchStatus.SENT,
            NotificationDispatchStatus.UNKNOWN);

    /** 카테고리·지역 구독은 단건 문자를 만든 시각부터 이 시간 동안 다음 빈자리를 발송 줄에 넣지 않는다. */
    private static final Duration CATEGORY_REGION_COOLDOWN = Duration.ofMinutes(60);

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationDispatchRepository notificationDispatchRepository;
    private final NotificationSubscriptionRepository notificationSubscriptionRepository;
    private final SubscriptionMatchService subscriptionMatchService;
    private final CategoryRegionPendingQueue categoryRegionPendingQueue;
    private final VacancySlotRepository vacancySlotRepository;
    private final AvailabilityService availabilityService;
    private final RecipientContactRepository recipientContactRepository;
    private final SolapiSmsClient solapiSmsClient;
    private final NotificationInboxService notificationInboxService;
    private final ObjectProvider<NotificationDispatchService> self;
    private final Clock clock;
    private final ShopStatusQueryRepository shopStatusQueryRepository;

    /**
     * PENDING 이벤트 하나를 고른다. 매장 구독 회원은 대기 작업을 넣고, 카테고리 구독은 쿨다운을 보고 나눈다.
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
        SubscriptionMatchGroups groups = subscriptionMatchService.findMatchGroups(
                event.getShopId(), event.getSlotStartsAt());

        int created = 0;
        // 매장 구독은 지금처럼 회원마다 발송 줄을 바로 만든다. 쿨다운 메모장에는 적지 않는다.
        for (Long userId : groups.storeUserIds()) {
            if (saveDispatchIfAbsent(event.getId(), userId)) {
                created++;
            }
        }
        created += createOrQueueCategoryRegion(event, groups.categoryRegionOnlySubscriptions());

        // 받을 회원이 없어도 이 이벤트 매칭은 끝이다. PENDING으로 두면 다음에 또 집는다.
        event.markProcessed();
        return Optional.of(created);
    }

    /**
     * 카테고리·지역 구독만 처리한다.
     * 마지막 발송 시각부터 60분이 지나지 않았으면 발송 줄을 만들지 않고 메모장에만 넣는다.
     * 60분이 지났거나 한 번도 안 보냈으면 발송 줄을 만들고, 그 순간 마지막 발송 시각을 적는다.
     * 문자가 나가기 전에 다음 이벤트가 또 바로 나가지 않게 하려는 것이다.
     *
     * @return 이번에 새로 만든 발송 줄 수
     */
    private int createOrQueueCategoryRegion(NotificationEvent event, List<NotificationSubscription> subscriptions) {
        int created = 0;
        LocalDateTime now = LocalDateTime.now(clock);
        for (NotificationSubscription subscription : subscriptions) {
            Long subscriptionId = subscription.getId();
            if (isCategoryRegionCoolingDown(subscriptionId, now)) {
                categoryRegionPendingQueue.addPendingEvent(subscriptionId, event.getId());
                continue;
            }
            if (saveDispatchIfAbsent(event.getId(), subscription.getUserId())) {
                created++;
            }
            categoryRegionPendingQueue.markSent(subscriptionId, now);
        }
        return created;
    }

    // 마지막 발송 시각이 있고, 그 시각부터 60분이 아직 안 지났으면 true.
    // 60분이 딱 지난 시각은 쿨다운이 끝난 것으로 본다.
    private boolean isCategoryRegionCoolingDown(Long subscriptionId, LocalDateTime now) {
        LocalDateTime lastSentAt = categoryRegionPendingQueue.lastSentAt(subscriptionId);
        if (lastSentAt == null || now == null) {
            return false;
        }
        return now.isBefore(lastSentAt.plus(CATEGORY_REGION_COOLDOWN));
    }

    // 같은 이벤트·회원이 이미 있으면 만들지 않는다. 대기·처리 중·생략·성공·실패·결과 불명도 한 줄만 둔다.
    private boolean saveDispatchIfAbsent(Long eventId, Long userId) {
        if (notificationDispatchRepository.existsByEventIdAndUserId(eventId, userId)) {
            return false;
        }
        notificationDispatchRepository.save(NotificationDispatch.builder()
                .eventId(eventId)
                .userId(userId)
                .build());
        return true;
    }

    /**
     * 마지막 발송 시각이 60분을 넘긴 카테고리·지역 구독만 본다.
     * 아직 안 보낸 이벤트를 하나씩 다시 확인해, 구독이 살아 있고 슬롯이 비어 있는 개수를 센다.
     * 개수는 매장 수가 아니라 슬롯 수다.
     * 1건 이상이면 "새 빈자리 N건" 한 통을 바로 보내고 목록을 비운 뒤 마지막 발송 시각을 지금으로 바꾼다.
     * 0건이면 문자는 보내지 않고 목록만 비운다. 마지막 발송 시각은 그대로라서 다음 빈자리는 다시 단건으로 나간다.
     * 묶음 문자는 발송 줄에 넣지 않는다.
     *
     * @return 이번에 문자 발송을 시도한 구독 수
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public int flushCategoryRegionSummaries() {
        LocalDateTime now = LocalDateTime.now(clock);
        int sent = 0;
        // 이미 트랜잭션이 있으면 그 안에서 이어서 본다. 없으면 구독자마다 커밋한 뒤 알림함을 남긴다.
        boolean joinCurrent = TransactionSynchronizationManager.isActualTransactionActive();
        for (Long subscriptionId : categoryRegionPendingQueue.subscriptionIds()) {
            try {
                SummaryResult result = joinCurrent
                        ? completeCategoryRegionSummary(subscriptionId, now)
                        : self.getObject().completeCategoryRegionSummary(subscriptionId, now);
                if (result.attempted()) {
                    sent++;
                    rememberInbox(result.inbox());
                }
            } catch (RuntimeException exception) {
                log.warn("카테고리 묶음 문자 처리 실패 subscriptionId={}", subscriptionId, exception);
            }
        }
        return sent;
    }

    /**
     * 구독 하나의 묶음 문자를 보내고 메모장을 갱신한다.
     * 호출한 쪽은 이 메서드가 끝난 뒤에 받은 알림을 저장한다.
     *
     * @param subscriptionId 카테고리·지역 구독 ID
     * @param now            기준 시각
     * @return 발송을 시도했는지와, 성공했을 때의 받은 알림
     */
    @Transactional
    public SummaryResult completeCategoryRegionSummary(Long subscriptionId, LocalDateTime now) {
        return sendCategoryRegionSummaryIfDue(subscriptionId, now);
    }

    // 60분이 지나지 않았거나, 쌓인 이벤트가 없으면 아무것도 하지 않는다.
    private SummaryResult sendCategoryRegionSummaryIfDue(Long subscriptionId, LocalDateTime now) {
        if (categoryRegionPendingQueue.lastSentAt(subscriptionId) == null) {
            return SummaryResult.skipped();
        }
        if (isCategoryRegionCoolingDown(subscriptionId, now)) {
            return SummaryResult.skipped();
        }
        List<Long> eventIds = categoryRegionPendingQueue.pendingEventIds(subscriptionId);
        if (eventIds.isEmpty()) {
            return SummaryResult.skipped();
        }

        // 건수와 날짜를 같은 목록에서 구한다. 두 번 조회하면 그사이 예약된 슬롯 때문에 숫자가 어긋날 수 있다.
        List<NotificationEvent> openEvents = openCategoryRegionEvents(subscriptionId, eventIds, now);
        if (openEvents.isEmpty()) {
            categoryRegionPendingQueue.clearPendingEvents(subscriptionId);
            log.info("카테고리 묶음 문자 생략(남은 빈자리 없음) subscriptionId={}", subscriptionId);
            return SummaryResult.skipped();
        }

        NotificationSubscription subscription = notificationSubscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            categoryRegionPendingQueue.clearPendingEvents(subscriptionId);
            return SummaryResult.skipped();
        }
        Optional<String> phone = recipientContactRepository.findPhoneByUserId(subscription.getUserId());
        if (phone.isEmpty() || !StringUtils.hasText(phone.get())) {
            // 번호가 없으면 목록을 지우지 않는다. 다음 1분에 다시 본다.
            log.warn("카테고리 묶음 문자 보류(전화번호 없음) subscriptionId={}, userId={}",
                    subscriptionId, subscription.getUserId());
            return SummaryResult.skipped();
        }

        int openSlotCount = openEvents.size();
        LocalDate earliestDate = earliestSlotDate(openEvents);
        String messageBody = "땡 잡을 기회 " + openSlotCount + "건이 생겼어요!";
        log.info("카테고리 묶음 문자 발송 시작 subscriptionId={}, userId={}, count={}, to={}",
                subscriptionId, subscription.getUserId(), openSlotCount, maskPhone(phone.get()));
        SmsSendResult result = solapiSmsClient.send(phone.get(), messageBody);
        logCategoryRegionSummaryResult(subscriptionId, subscription.getUserId(), result);
        // 성공이든 실패든 같은 묶음을 다음 1분에 다시 보내지 않는다.
        categoryRegionPendingQueue.clearPendingEvents(subscriptionId);
        categoryRegionPendingQueue.markSent(subscriptionId, now);
        if (result.status() != SmsSendStatus.SUCCESS) {
            return SummaryResult.attempted(null);
        }
        return SummaryResult.attempted(new SmsInbox(
                subscription.getUserId(),
                NotificationInboxCategory.INTEREST,
                "빈자리 알림",
                messageBody,
                NotificationInboxService.categoryRegionListPath(
                        subscription.getCategoryId(), subscription.getRegionCode(), earliestDate)));
    }

    // 남은 빈자리 중 가장 빠른 날. 목록이 비면 null 이다.
    private static LocalDate earliestSlotDate(List<NotificationEvent> openEvents) {
        LocalDate earliest = null;
        for (NotificationEvent event : openEvents) {
            LocalDate slotDate = event.getSlotStartsAt().toLocalDate();
            if (earliest == null || slotDate.isBefore(earliest)) {
                earliest = slotDate;
            }
        }
        return earliest;
    }

    // 아직 팔리고 구독과도 맞는 묶음 대기 이벤트만 모은다.
    private List<NotificationEvent> openCategoryRegionEvents(Long subscriptionId, List<Long> eventIds,
            LocalDateTime now) {
        NotificationSubscription subscription = notificationSubscriptionRepository.findById(subscriptionId).orElse(null);
        List<NotificationEvent> openEvents = new ArrayList<>();
        for (Long eventId : eventIds) {
            NotificationEvent event = notificationEventRepository.findById(eventId).orElse(null);
            if (event == null) {
                continue;
            }
            if (!categorySubscriptionStillApplies(subscription, event, now)) {
                continue;
            }
            if (!slotStillSellable(event)) {
                continue;
            }
            openEvents.add(event);
        }
        return openEvents;
    }

    // 해제한 구독, 희망 종료가 지난 구독, 빈자리 시작이 희망 시간 밖인 구독은 세지 않는다.
    private boolean categorySubscriptionStillApplies(NotificationSubscription subscription, NotificationEvent event,
            LocalDateTime now) {
        if (subscription == null || !subscription.isActive() || now == null) {
            return false;
        }
        if (subscription.getEndTime() == null || !subscription.getEndTime().isAfter(now)) {
            return false;
        }
        LocalDateTime slotStartsAt = event.getSlotStartsAt();
        if (slotStartsAt == null || subscription.getStartTime() == null) {
            return false;
        }
        if (subscription.getStartTime().isAfter(slotStartsAt)) {
            return false;
        }
        return !subscription.getEndTime().isBefore(slotStartsAt);
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
     * 재검사를 통과한 작업에 문자를 보내고 결과를 저장한 뒤, 성공이면 받은 알림을 남긴다.
     * 발송 트랜잭션을 먼저 끝내고 알림함을 저장한다. 알림함 저장이 실패해도 이미 보낸 문자는 다시 보내지 않는다.
     *
     * @param job 선점했고 재검사를 통과한 발송 대기 작업
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void sendClaimedJob(NotificationDispatch job) {
        SmsInbox inbox = TransactionSynchronizationManager.isActualTransactionActive()
                ? completeClaimedJob(job)
                : self.getObject().completeClaimedJob(job);
        rememberInbox(inbox);
    }

    /**
     * 문자를 보내고 결과만 저장한다. 받은 알림은 반환만 하고, 호출한 쪽이 발송 커밋 뒤에 저장한다.
     * 성공은 SENT, 실패는 FAILED, 응답이 애매하면 UNKNOWN 이다. 실패·불명을 성공으로 바꾸지 않는다.
     * 같은 이벤트·회원에 이미 SENT 또는 UNKNOWN 이 있으면 문자를 다시 보내지 않는다.
     *
     * @param job 선점했고 재검사를 통과한 발송 대기 작업
     * @return 문자 발송에 성공했으면 받은 알림. 아니면 null
     */
    @Transactional
    public SmsInbox completeClaimedJob(NotificationDispatch job) {
        if (job == null || job.getId() == null) {
            return null;
        }

        // 메모리 값만 믿지 않고 DB 최신 상태를 본다.
        NotificationDispatch latest = notificationDispatchRepository.findById(job.getId()).orElse(null);
        if (latest == null) {
            return null;
        }
        // 같은 이벤트·회원에 이미 SENT 또는 UNKNOWN 이면 문자를 다시 보내지 않는다.
        if (alreadySentOrUnknown(latest.getEventId(), latest.getUserId())) {
            log.info("문자 재발송 생략(이미 SENT/UNKNOWN) dispatchId={}, userId={}, eventId={}, status={}",
                    latest.getId(), latest.getUserId(), latest.getEventId(), latest.getStatus());
            return null;
        }
        if (latest.getStatus() != NotificationDispatchStatus.PROCESSING) {
            return null;
        }

        NotificationEvent event = notificationEventRepository.findById(latest.getEventId()).orElse(null);
        if (event == null) {
            String reason = "알림 이벤트를 찾을 수 없습니다.";
            latest.markFailed(reason);
            notificationDispatchRepository.save(latest);
            log.warn("문자 발송 실패 dispatchId={}, userId={}, reason={}",
                    latest.getId(), latest.getUserId(), reason);
            return null;
        }

        Optional<String> phone = recipientContactRepository.findPhoneByUserId(latest.getUserId());
        if (phone.isEmpty() || !StringUtils.hasText(phone.get())) {
            String reason = "회원 전화번호가 없습니다.";
            latest.markFailed(reason);
            notificationDispatchRepository.save(latest);
            log.warn("문자 발송 실패 dispatchId={}, userId={}, reason={}",
                    latest.getId(), latest.getUserId(), reason);
            return null;
        }

        String messageBody = buildMessage(event);
        log.info("문자 발송 시작 dispatchId={}, userId={}, eventId={}, to={}",
                latest.getId(), latest.getUserId(), event.getId(), maskPhone(phone.get()));
        SmsSendResult result = solapiSmsClient.send(phone.get(), messageBody);
        applySendResult(latest, result);
        notificationDispatchRepository.save(latest);
        logSendResult(latest, result);
        if (result.status() != SmsSendStatus.SUCCESS) {
            return null;
        }
        return new SmsInbox(
                latest.getUserId(),
                NotificationInboxCategory.INTEREST,
                interestTitle(event.getEventType()),
                messageBody,
                NotificationInboxService.shopPath(event.getShopId()));
    }

    // 같은 이벤트·회원에 성공(SENT) 또는 결과 불명(UNKNOWN)이 있으면 true. 이 둘은 재발송하지 않는다.
    private boolean alreadySentOrUnknown(Long eventId, Long userId) {
        return notificationDispatchRepository.existsByEventIdAndUserIdAndStatusIn(
                eventId, userId, DO_NOT_RESEND);
    }

    // 발송 트랜잭션이 끝난 뒤에 호출한다. 저장이 실패해도 이미 보낸 문자는 다시 보내지 않는다.
    private void rememberInbox(SmsInbox inbox) {
        if (inbox == null) {
            return;
        }
        try {
            notificationInboxService.save(
                    inbox.userId(), inbox.category(), inbox.title(), inbox.body(), inbox.linkPath());
        } catch (RuntimeException exception) {
            log.warn("받은 알림 저장 실패. 이미 발송한 문자는 유지한다 userId={}", inbox.userId(), exception);
        }
    }

    // 종 목록 제목. 본문과 달리 짧은 이름만 둔다.
    private static String interestTitle(NotificationEventType eventType) {
        return switch (eventType) {
            case IMMINENT -> "임박 알림";
            case LAST_MINUTE -> "초임박 알림";
            case REOPENED -> "빈자리 알림";
        };
    }

    // 본문은 같고, 앞에만 임박! / 초임박! / 땡! 을 붙인다.
    private String buildMessage(NotificationEvent event) {
        String shopName = recipientContactRepository.findShopNameById(event.getShopId()).orElse("매장");
        String startsAtText = event.getSlotStartsAt().format(SLOT_START_TEXT);
        String prefix = switch (event.getEventType()) {
            case IMMINENT -> "임박! ";
            case LAST_MINUTE -> "초임박! ";
            case REOPENED -> "땡! ";
        };
        return prefix + shopName + "-" + startsAtText + " 땡잡을 기회가 생겼어요!";
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

    private void logSendResult(NotificationDispatch job, SmsSendResult result) {
        if (result.status() == SmsSendStatus.SUCCESS) {
            log.info("문자 발송 성공 dispatchId={}, userId={}, providerMessageId={}",
                    job.getId(), job.getUserId(), result.providerMessageId());
            return;
        }
        if (result.status() == SmsSendStatus.UNKNOWN) {
            log.warn("문자 발송 결과 불명 dispatchId={}, userId={}, reason={}",
                    job.getId(), job.getUserId(), result.failReason());
            return;
        }
        log.warn("문자 발송 실패 dispatchId={}, userId={}, reason={}",
                job.getId(), job.getUserId(), result.failReason());
    }

    private void logCategoryRegionSummaryResult(Long subscriptionId, Long userId, SmsSendResult result) {
        if (result.status() == SmsSendStatus.SUCCESS) {
            log.info("카테고리 묶음 문자 발송 성공 subscriptionId={}, userId={}, providerMessageId={}",
                    subscriptionId, userId, result.providerMessageId());
            return;
        }
        if (result.status() == SmsSendStatus.UNKNOWN) {
            log.warn("카테고리 묶음 문자 결과 불명 subscriptionId={}, userId={}, reason={}",
                    subscriptionId, userId, result.failReason());
            return;
        }
        log.warn("카테고리 묶음 문자 발송 실패 subscriptionId={}, userId={}, reason={}",
                subscriptionId, userId, result.failReason());
    }

    private static String maskPhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return "****";
        }
        return "****" + digits.substring(digits.length() - 4);
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

    // 재공개는 화면과 같은 isAvailable 로 본다. 임박·초임박은 당분간 OPEN 기준을 유지한다.
    private boolean slotStillSellable(NotificationEvent event) {
        if (event.getEventType() == NotificationEventType.REOPENED) {
            if (!shopStatusQueryRepository.isActive(event.getShopId())) {
                return false;
            }
            int durationMinutes = (int) Duration.between(event.getSlotStartsAt(), event.getSlotEndsAt()).toMinutes();
            if (durationMinutes <= 0) {
                return false;
            }
            return availabilityService.isAvailable(
                    event.getShopId(), event.getSlotStartsAt(), durationMinutes);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return vacancySlotRepository.isOpenSlotSellable(event.getExceptionId(), now);
    }

    // 확인에 걸린 줄은 보내지 않고 끝 상태로 둔다.
    private void finishWithoutSending(NotificationDispatch job) {
        job.markSkipped();
        notificationDispatchRepository.save(job);
        log.info("문자 발송 생략(SKIPPED) dispatchId={}, userId={}, eventId={}",
                job.getId(), job.getUserId(), job.getEventId());
    }

    /** 묶음 문자 한 건을 시도했는지와, 성공했을 때 남길 받은 알림. */
    public record SummaryResult(boolean attempted, SmsInbox inbox) {

        private static SummaryResult skipped() {
            return new SummaryResult(false, null);
        }

        private static SummaryResult attempted(SmsInbox inbox) {
            return new SummaryResult(true, inbox);
        }
    }

    /** 문자 발송에 성공한 뒤 받은 알림에 남길 한 줄. */
    public record SmsInbox(
            Long userId,
            NotificationInboxCategory category,
            String title,
            String body,
            String linkPath) {
    }
}
