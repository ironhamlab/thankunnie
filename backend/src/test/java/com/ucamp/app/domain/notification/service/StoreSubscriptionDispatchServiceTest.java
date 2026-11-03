package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
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

class StoreSubscriptionDispatchServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long SHOP_ID = 1L;
    private static final LocalDateTime HOPE_START = LocalDateTime.of(2026, 10, 31, 17, 3);
    private static final LocalDateTime HOPE_END = LocalDateTime.of(2026, 10, 31, 21, 0);

    private ExistingVacancyTargetService existingVacancyTargetService;
    private StoreSubscriptionTreatmentService storeSubscriptionTreatmentService;
    private NotificationDispatchRepository notificationDispatchRepository;
    private StoreSubscriptionDispatchService storeSubscriptionDispatchService;

    @BeforeEach
    void setUp() {
        existingVacancyTargetService = mock(ExistingVacancyTargetService.class);
        storeSubscriptionTreatmentService = mock(StoreSubscriptionTreatmentService.class);
        notificationDispatchRepository = mock(NotificationDispatchRepository.class);
        lenient().when(storeSubscriptionTreatmentService.canBook(anyLong(), anyLong(), any()))
                .thenReturn(true);
        storeSubscriptionDispatchService = new StoreSubscriptionDispatchService(
                existingVacancyTargetService, storeSubscriptionTreatmentService, notificationDispatchRepository);
    }

    @Test
    @DisplayName("희망 시간이 있는 업장 구독은 그 회원만 발송 줄을 만든다")
    void enqueue_storeWithHopeTime_savesOnlyMissingRows() {
        NotificationSubscription subscription = storeSubscription(HOPE_START, HOPE_END);
        NotificationEvent first = event(10L);
        NotificationEvent second = event(11L);
        given(existingVacancyTargetService.findForStore(SHOP_ID, HOPE_START, HOPE_END))
                .willReturn(List.of(first, second));
        given(notificationDispatchRepository.existsByEventIdAndUserId(10L, USER_ID)).willReturn(true);
        given(notificationDispatchRepository.existsByEventIdAndUserId(11L, USER_ID)).willReturn(false);

        storeSubscriptionDispatchService.enqueue(subscription);

        ArgumentCaptor<NotificationDispatch> saved = ArgumentCaptor.forClass(NotificationDispatch.class);
        verify(notificationDispatchRepository).save(saved.capture());
        assertThat(saved.getValue().getEventId()).isEqualTo(11L);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("고른 시술을 그 시각에 예약할 수 없으면 줄을 만들지 않는다")
    void enqueue_treatmentNotBookable_doesNotSave() {
        NotificationSubscription subscription = storeSubscription(HOPE_START, HOPE_END);
        given(existingVacancyTargetService.findForStore(SHOP_ID, HOPE_START, HOPE_END))
                .willReturn(List.of(event(10L)));
        given(storeSubscriptionTreatmentService.canBook(eq(SHOP_ID), eq(100L), eq(event(10L).getSlotStartsAt())))
                .willReturn(false);

        storeSubscriptionDispatchService.enqueue(subscription);

        verify(notificationDispatchRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 이벤트·회원의 줄이 있으면 다시 만들지 않는다")
    void enqueue_existingRow_doesNotSaveAgain() {
        NotificationSubscription subscription = storeSubscription(HOPE_START, HOPE_END);
        given(existingVacancyTargetService.findForStore(SHOP_ID, HOPE_START, HOPE_END))
                .willReturn(List.of(event(10L)));
        given(notificationDispatchRepository.existsByEventIdAndUserId(10L, USER_ID)).willReturn(true);

        storeSubscriptionDispatchService.enqueue(subscription);

        verify(notificationDispatchRepository, never()).save(any());
    }

    @Test
    @DisplayName("희망 시간이 없거나 업장 구독이 아니면 줄을 만들지 않는다")
    void enqueue_withoutStoreHopeTime_doesNothing() {
        storeSubscriptionDispatchService.enqueue(null);
        storeSubscriptionDispatchService.enqueue(storeSubscription(null, null));
        storeSubscriptionDispatchService.enqueue(NotificationSubscription.builder()
                .userId(USER_ID)
                .subscriptionType(SubscriptionType.CATEGORY_REGION)
                .categoryId(10L)
                .regionCode("SEOUL_GANGNAM")
                .startTime(HOPE_START)
                .endTime(HOPE_END)
                .build());

        verify(existingVacancyTargetService, never()).findForStore(any(), any(), any());
        verify(notificationDispatchRepository, never()).save(any());
    }

    private static NotificationSubscription storeSubscription(LocalDateTime start, LocalDateTime end) {
        return NotificationSubscription.builder()
                .userId(USER_ID)
                .subscriptionType(SubscriptionType.STORE)
                .treatmentId(100L)
                .shopId(SHOP_ID)
                .startTime(start)
                .endTime(end)
                .build();
    }

    private static NotificationEvent event(Long eventId) {
        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.LAST_MINUTE)
                .exceptionId(1L)
                .shopId(SHOP_ID)
                .slotStartsAt(HOPE_START.plusHours(1))
                .slotEndsAt(HOPE_START.plusHours(2))
                .build();
        ReflectionTestUtils.setField(event, "id", eventId);
        return event;
    }
}
