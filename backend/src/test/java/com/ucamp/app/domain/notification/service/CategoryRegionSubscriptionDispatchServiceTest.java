package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventType;
import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionType;
import com.ucamp.app.domain.notification.repository.NotificationDispatchRepository;

class CategoryRegionSubscriptionDispatchServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 17, 0);
    private static final Long USER_ID = 7L;
    private static final Long SUBSCRIPTION_ID = 20L;
    private static final LocalDateTime HOPE_START = LocalDateTime.of(2026, 10, 31, 17, 3);
    private static final LocalDateTime HOPE_END = LocalDateTime.of(2026, 10, 31, 21, 0);

    private ExistingVacancyTargetService existingVacancyTargetService;
    private NotificationDispatchRepository notificationDispatchRepository;
    private CategoryRegionPendingQueue categoryRegionPendingQueue;
    private CategoryRegionSubscriptionDispatchService categoryRegionSubscriptionDispatchService;

    @BeforeEach
    void setUp() {
        existingVacancyTargetService = mock(ExistingVacancyTargetService.class);
        notificationDispatchRepository = mock(NotificationDispatchRepository.class);
        categoryRegionPendingQueue = new CategoryRegionPendingQueue();
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        categoryRegionSubscriptionDispatchService = new CategoryRegionSubscriptionDispatchService(
                existingVacancyTargetService,
                notificationDispatchRepository,
                categoryRegionPendingQueue,
                clock);
    }

    @Test
    @DisplayName("이미 열린 빈자리가 여러 건이면 첫 건만 발송 줄로 만들고 나머지는 묶음에 넣는다")
    void enqueue_severalEvents_savesFirstAndQueuesTheRest() {
        NotificationSubscription subscription = categorySubscription();
        NotificationEvent first = event(10L, HOPE_START.plusHours(1));
        NotificationEvent second = event(11L, HOPE_START.plusHours(2));
        given(existingVacancyTargetService.findForCategoryRegion(10L, "SEOUL_GANGNAM", HOPE_START, HOPE_END))
                .willReturn(List.of(first, second));
        given(notificationDispatchRepository.existsByEventIdAndUserId(10L, USER_ID)).willReturn(false);

        categoryRegionSubscriptionDispatchService.enqueue(subscription);

        ArgumentCaptor<NotificationDispatch> saved = ArgumentCaptor.forClass(NotificationDispatch.class);
        verify(notificationDispatchRepository).save(saved.capture());
        assertThat(saved.getValue().getEventId()).isEqualTo(10L);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(categoryRegionPendingQueue.pendingEventIds(SUBSCRIPTION_ID)).containsExactly(11L);
        assertThat(categoryRegionPendingQueue.lastSentAt(SUBSCRIPTION_ID)).isEqualTo(NOW);
    }

    @Test
    @DisplayName("같은 이벤트·회원의 줄이 있으면 다시 만들지 않는다")
    void enqueue_existingRow_doesNotSaveAgain() {
        NotificationSubscription subscription = categorySubscription();
        given(existingVacancyTargetService.findForCategoryRegion(10L, "SEOUL_GANGNAM", HOPE_START, HOPE_END))
                .willReturn(List.of(event(10L, HOPE_START.plusHours(1))));
        given(notificationDispatchRepository.existsByEventIdAndUserId(10L, USER_ID)).willReturn(true);

        categoryRegionSubscriptionDispatchService.enqueue(subscription);

        verify(notificationDispatchRepository, never()).save(any());
        assertThat(categoryRegionPendingQueue.pendingEventIds(SUBSCRIPTION_ID)).isEmpty();
    }

    @Test
    @DisplayName("카테고리·지역 구독이 아니거나 구독 ID가 없으면 줄을 만들지 않는다")
    void enqueue_notCategoryRegion_doesNothing() {
        categoryRegionSubscriptionDispatchService.enqueue(null);
        categoryRegionSubscriptionDispatchService.enqueue(NotificationSubscription.builder()
                .userId(USER_ID)
                .subscriptionType(SubscriptionType.STORE)
                .shopId(1L)
                .startTime(HOPE_START)
                .endTime(HOPE_END)
                .build());

        verify(existingVacancyTargetService, never()).findForCategoryRegion(any(), any(), any(), any());
        verify(notificationDispatchRepository, never()).save(any());
    }

    private static NotificationSubscription categorySubscription() {
        NotificationSubscription subscription = NotificationSubscription.builder()
                .userId(USER_ID)
                .subscriptionType(SubscriptionType.CATEGORY_REGION)
                .categoryId(10L)
                .regionCode("SEOUL_GANGNAM")
                .startTime(HOPE_START)
                .endTime(HOPE_END)
                .build();
        ReflectionTestUtils.setField(subscription, "id", SUBSCRIPTION_ID);
        return subscription;
    }

    private static NotificationEvent event(Long eventId, LocalDateTime startsAt) {
        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.LAST_MINUTE)
                .exceptionId(1L)
                .shopId(1L)
                .slotStartsAt(startsAt)
                .slotEndsAt(startsAt.plusHours(1))
                .build();
        ReflectionTestUtils.setField(event, "id", eventId);
        return event;
    }
}
