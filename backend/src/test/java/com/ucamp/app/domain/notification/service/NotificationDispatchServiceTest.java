package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.notification.NotificationFixture;
import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;
import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.entity.NotificationDispatchStatus;
import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationInbox;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.entity.NotificationEventStatus;
import com.ucamp.app.domain.notification.entity.NotificationEventType;
import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionType;
import com.ucamp.app.domain.notification.repository.NotificationDispatchRepository;
import com.ucamp.app.domain.notification.repository.NotificationEventRepository;
import com.ucamp.app.domain.notification.repository.NotificationInboxRepository;
import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.ShopCategoryRegionRepository;
import com.ucamp.app.domain.notification.repository.VacancySlotRepository;
import com.ucamp.app.domain.vacancy.repository.ShopStatusQueryRepository;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationDispatchService.class, NotificationInboxService.class, NotificationInboxConnections.class,
        SubscriptionMatchService.class,
        ShopCategoryRegionRepository.class, VacancySlotRepository.class, RecipientContactRepository.class,
        AvailabilityService.class, ShopStatusQueryRepository.class, CategoryRegionPendingQueue.class,
        NotificationDispatchServiceTest.FixedClock.class})
@ActiveProfiles("test")
class NotificationDispatchServiceTest {

    // 2026-10-22 은 목요일(THU). 영업시간 픽스처와 맞춰 둔다.
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);
    private static final LocalDateTime SLOT_START = NOW.plusHours(3);
    private static final long SHOP_ID = 1L;
    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private NotificationDispatchRepository notificationDispatchRepository;

    @Autowired
    private NotificationInboxRepository notificationInboxRepository;

    @Autowired
    private NotificationSubscriptionRepository notificationSubscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CategoryRegionPendingQueue categoryRegionPendingQueue;

    @MockitoBean
    private SolapiSmsClient solapiSmsClient;

    @BeforeEach
    void setUp() {
        categoryRegionPendingQueue.clear();
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.shop(jdbcTemplate, SHOP_ID, 10L, "SEOUL_GANGNAM", "ACTIVE");
        NotificationFixture.user(jdbcTemplate, USER_ID);
        NotificationFixture.user(jdbcTemplate, OTHER_USER_ID);
        given(solapiSmsClient.send(anyString(), anyString())).willReturn(SmsSendResult.success("G4V_TEST"));
    }

    @Test
    @DisplayName("PENDING 이벤트의 맞는 회원마다 대기 작업을 넣고 이벤트를 PROCESSED로 바꾼다")
    void createDispatchJobs_matchingUsers_savesPendingJobs() {
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        notificationSubscriptionRepository.save(store(USER_ID));
        notificationSubscriptionRepository.save(store(OTHER_USER_ID));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(2);
        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getUserId)
                .containsExactlyInAnyOrder(USER_ID, OTHER_USER_ID);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getStatus)
                .containsOnly(NotificationDispatchStatus.PENDING);
    }

    @Test
    @DisplayName("같은 이벤트·회원이 이미 있으면 그 회원은 건너뛰고 이벤트는 PROCESSED가 된다")
    void createDispatchJobs_existingUser_skipsThatUser() {
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        notificationDispatchRepository.saveAndFlush(NotificationDispatch.builder()
                .eventId(event.getId())
                .userId(USER_ID)
                .build());
        notificationSubscriptionRepository.save(store(USER_ID));
        notificationSubscriptionRepository.save(store(OTHER_USER_ID));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(1);
        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getUserId)
                .containsExactlyInAnyOrder(USER_ID, OTHER_USER_ID);
    }

    @Test
    @DisplayName("한 번 호출에는 ID가 작은 PENDING 이벤트 하나만 처리한다")
    void createDispatchJobs_twoPendingEvents_processesOne() {
        NotificationEvent first = notificationEventRepository.saveAndFlush(event(1L));
        NotificationEvent second = notificationEventRepository.saveAndFlush(event(2L));
        notificationSubscriptionRepository.save(store(USER_ID));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(1);
        assertThat(first.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(second.getStatus()).isEqualTo(NotificationEventStatus.PENDING);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getEventId)
                .containsExactly(first.getId());
    }

    @Test
    @DisplayName("맞는 회원이 없어도 이벤트는 PROCESSED가 되고 대기 작업은 없다")
    void createDispatchJobs_noRecipient_marksProcessed() {
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(0);
        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(notificationDispatchRepository.count()).isZero();
    }

    @Test
    @DisplayName("PENDING 이벤트가 없으면 대기 작업을 만들지 않는다")
    void createDispatchJobs_noPendingEvent_createsNothing() {
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        event.markProcessed();
        notificationEventRepository.saveAndFlush(event);

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).isEmpty();
        assertThat(notificationDispatchRepository.count()).isZero();
    }

    @Test
    @DisplayName("카테고리 구독의 첫 빈자리는 발송 줄을 만들고 그 시각을 마지막 발송 시각으로 적는다")
    void createDispatchJobs_categoryRegionFirstEvent_savesJobAndMarksSent() {
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        NotificationSubscription subscription = notificationSubscriptionRepository.save(categoryRegion(USER_ID, NOW.plusHours(5)));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(1);
        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getUserId)
                .containsExactly(USER_ID);
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isEqualTo(NOW);
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).isEmpty();
    }

    @Test
    @DisplayName("카테고리 구독은 마지막 발송 시각부터 60분 안에는 발송 줄을 만들지 않고 메모장에만 넣는다")
    void createDispatchJobs_categoryRegionWithin60Minutes_queuesWithoutDispatch() {
        NotificationSubscription subscription = notificationSubscriptionRepository.save(categoryRegion(USER_ID, NOW.plusHours(6)));
        notificationEventRepository.saveAndFlush(event(1L));
        notificationDispatchService.createDispatchJobs();

        NotificationEvent second = notificationEventRepository.saveAndFlush(event(2L));
        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(0);
        assertThat(second.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(notificationDispatchRepository.findAll()).hasSize(1);
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isEqualTo(NOW);
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).containsExactly(second.getId());
    }

    @Test
    @DisplayName("카테고리 구독은 마지막 발송 시각부터 60분이 지나면 다시 발송 줄을 만든다")
    void createDispatchJobs_categoryRegionAfter60Minutes_savesJobAgain() {
        NotificationSubscription subscription = notificationSubscriptionRepository.save(categoryRegion(USER_ID, NOW.plusHours(5)));
        categoryRegionPendingQueue.markSent(subscription.getId(), NOW.minusMinutes(60));
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(1);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getEventId)
                .containsExactly(event.getId());
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isEqualTo(NOW);
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).isEmpty();
    }

    @Test
    @DisplayName("카테고리 구독은 마지막 발송 시각부터 59분이면 아직 메모장에만 넣는다")
    void createDispatchJobs_categoryRegionAt59Minutes_queuesWithoutDispatch() {
        NotificationSubscription subscription = notificationSubscriptionRepository.save(categoryRegion(USER_ID, NOW.plusHours(5)));
        categoryRegionPendingQueue.markSent(subscription.getId(), NOW.minusMinutes(59));
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(0);
        assertThat(notificationDispatchRepository.count()).isZero();
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isEqualTo(NOW.minusMinutes(59));
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).containsExactly(event.getId());
    }

    @Test
    @DisplayName("매장 구독은 쿨다운 메모장에 적지 않는다")
    void createDispatchJobs_storeUser_doesNotTouchCategoryQueue() {
        notificationEventRepository.saveAndFlush(event(1L));
        notificationSubscriptionRepository.save(store(USER_ID));

        notificationDispatchService.createDispatchJobs();

        assertThat(notificationDispatchRepository.count()).isEqualTo(1);
        assertThat(categoryRegionPendingQueue.lastSentAt(1L)).isNull();
        assertThat(categoryRegionPendingQueue.pendingEventIds(1L)).isEmpty();
    }

    @Test
    @DisplayName("매장 구독으로 알림을 받는 회원은 카테고리 구독이 있어도 메모장에 넣지 않는다")
    void createDispatchJobs_storeAndCategorySameUser_savesStoreJobOnly() {
        notificationEventRepository.saveAndFlush(event(1L));
        notificationSubscriptionRepository.save(store(USER_ID));
        NotificationSubscription category = notificationSubscriptionRepository.save(categoryRegion(USER_ID, NOW.plusHours(5)));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(1);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getUserId)
                .containsExactly(USER_ID);
        assertThat(categoryRegionPendingQueue.lastSentAt(category.getId())).isNull();
        assertThat(categoryRegionPendingQueue.pendingEventIds(category.getId())).isEmpty();
    }

    @Test
    @DisplayName("같은 회원의 카테고리 구독이 두 개여도 발송 줄은 하나고 둘 다 마지막 발송 시각을 적는다")
    void createDispatchJobs_twoCategorySubscriptions_oneJobAndBothMarkedSent() {
        notificationEventRepository.saveAndFlush(event(1L));
        NotificationSubscription first = notificationSubscriptionRepository.save(categoryRegion(USER_ID, NOW.plusHours(5)));
        NotificationSubscription second = notificationSubscriptionRepository.save(categoryRegion(USER_ID, NOW.plusHours(6)));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(1);
        assertThat(notificationDispatchRepository.count()).isEqualTo(1);
        assertThat(categoryRegionPendingQueue.lastSentAt(first.getId())).isEqualTo(NOW);
        assertThat(categoryRegionPendingQueue.lastSentAt(second.getId())).isEqualTo(NOW);
        assertThat(categoryRegionPendingQueue.pendingEventIds(first.getId())).isEmpty();
        assertThat(categoryRegionPendingQueue.pendingEventIds(second.getId())).isEmpty();
    }

    @Test
    @DisplayName("쿨다운 중인 구독만 메모장에 넣고 쿨다운이 아닌 구독은 발송 줄을 만든다")
    void createDispatchJobs_oneCoolingAndOneReady_queuesOnlyTheCoolingSubscription() {
        NotificationSubscription cooling = notificationSubscriptionRepository.save(categoryRegion(USER_ID, NOW.plusHours(5)));
        NotificationSubscription ready = notificationSubscriptionRepository.save(categoryRegion(OTHER_USER_ID, NOW.plusHours(5)));
        categoryRegionPendingQueue.markSent(cooling.getId(), NOW.minusMinutes(10));
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(1);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getUserId)
                .containsExactly(OTHER_USER_ID);
        assertThat(categoryRegionPendingQueue.pendingEventIds(cooling.getId())).containsExactly(event.getId());
        assertThat(categoryRegionPendingQueue.lastSentAt(cooling.getId())).isEqualTo(NOW.minusMinutes(10));
        assertThat(categoryRegionPendingQueue.pendingEventIds(ready.getId())).isEmpty();
        assertThat(categoryRegionPendingQueue.lastSentAt(ready.getId())).isEqualTo(NOW);
    }

    @Test
    @DisplayName("같은 회원이어도 쿨다운 중인 구독만 메모장에 넣고 다른 구독은 발송 줄을 만든다")
    void createDispatchJobs_sameUserOneCoolingAndOneReady_queuesOnlyTheCoolingSubscription() {
        NotificationSubscription cooling = notificationSubscriptionRepository.save(categoryRegion(USER_ID, NOW.plusHours(5)));
        NotificationSubscription ready = notificationSubscriptionRepository.save(categoryRegion(USER_ID, NOW.plusHours(6)));
        categoryRegionPendingQueue.markSent(cooling.getId(), NOW.minusMinutes(10));
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(1);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getUserId)
                .containsExactly(USER_ID);
        assertThat(categoryRegionPendingQueue.pendingEventIds(cooling.getId())).containsExactly(event.getId());
        assertThat(categoryRegionPendingQueue.pendingEventIds(ready.getId())).isEmpty();
        assertThat(categoryRegionPendingQueue.lastSentAt(ready.getId())).isEqualTo(NOW);
    }

    @Test
    @DisplayName("60분이 지난 카테고리 구독은 빈 슬롯 수만큼 묶음 문자 한 통을 보내고 발송 줄은 만들지 않는다")
    void flushCategoryRegionSummaries_openSlots_sendsOneSummaryWithoutDispatch() {
        NotificationSubscription subscription = notificationSubscriptionRepository.save(
                categoryRegion(USER_ID, NOW.plusHours(6)));
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        openSlot(2L, SLOT_START.plusHours(1), SLOT_START.plusHours(2));
        NotificationEvent first = notificationEventRepository.saveAndFlush(event(1L));
        NotificationEvent second = notificationEventRepository.saveAndFlush(event(2L));
        categoryRegionPendingQueue.markSent(subscription.getId(), NOW.minusMinutes(60));
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), first.getId());
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), second.getId());

        int sent = notificationDispatchService.flushCategoryRegionSummaries();

        assertThat(sent).isEqualTo(1);
        assertThat(notificationDispatchRepository.count()).isZero();
        verify(solapiSmsClient).send(eq("010-1111-1111"), eq("땡 잡을 기회 2건이 생겼어요!"));
        assertThat(notificationInboxRepository.findAll()).singleElement().satisfies(inbox -> {
            assertThat(inbox.getUserId()).isEqualTo(USER_ID);
            assertThat(inbox.getCategory()).isEqualTo(NotificationInboxCategory.INTEREST);
            assertThat(inbox.getTitle()).isEqualTo("빈자리 알림");
            assertThat(inbox.getBody()).isEqualTo("땡 잡을 기회 2건이 생겼어요!");
            assertThat(inbox.getLinkPath()).isEqualTo(NotificationInboxService.TIME_SALE_PATH);
            assertThat(inbox.getReadAt()).isNull();
        });
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).isEmpty();
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isEqualTo(NOW);
    }

    @Test
    @DisplayName("한 구독자 문자 발송이 실패해도 다른 구독자의 묶음 문자는 보낸다")
    void flushCategoryRegionSummaries_oneSendThrows_continuesNextSubscriber() {
        NotificationSubscription failed = notificationSubscriptionRepository.save(
                categoryRegion(USER_ID, NOW.plusHours(6)));
        NotificationSubscription sent = notificationSubscriptionRepository.save(
                categoryRegion(OTHER_USER_ID, NOW.plusHours(6)));
        jdbcTemplate.update("UPDATE APP_USER SET PHONE = '010-2222-2222' WHERE USER_ID = ?", OTHER_USER_ID);
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        NotificationEvent open = notificationEventRepository.saveAndFlush(event(1L));
        categoryRegionPendingQueue.markSent(failed.getId(), NOW.minusMinutes(60));
        categoryRegionPendingQueue.markSent(sent.getId(), NOW.minusMinutes(60));
        categoryRegionPendingQueue.addPendingEvent(failed.getId(), open.getId());
        categoryRegionPendingQueue.addPendingEvent(sent.getId(), open.getId());
        given(solapiSmsClient.send(eq("010-1111-1111"), anyString()))
                .willThrow(new IllegalStateException("발송 오류"));
        given(solapiSmsClient.send(eq("010-2222-2222"), anyString()))
                .willReturn(SmsSendResult.success("OK"));

        int count = notificationDispatchService.flushCategoryRegionSummaries();

        assertThat(count).isEqualTo(1);
        verify(solapiSmsClient).send(eq("010-2222-2222"), eq("땡 잡을 기회 1건이 생겼어요!"));
        assertThat(notificationInboxRepository.findAll())
                .extracting(NotificationInbox::getUserId)
                .containsExactly(OTHER_USER_ID);
        assertThat(categoryRegionPendingQueue.pendingEventIds(sent.getId())).isEmpty();
    }

    @Test
    @DisplayName("이미 예약된 슬롯은 묶음 건수에서 빼고 남은 슬롯만 센다")
    void flushCategoryRegionSummaries_oneSlotClosed_countsOnlyOpenSlot() {
        NotificationSubscription subscription = notificationSubscriptionRepository.save(
                categoryRegion(USER_ID, NOW.plusHours(6)));
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        NotificationEvent open = notificationEventRepository.saveAndFlush(event(1L));
        NotificationEvent closed = notificationEventRepository.saveAndFlush(event(2L));
        categoryRegionPendingQueue.markSent(subscription.getId(), NOW.minusMinutes(60));
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), open.getId());
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), closed.getId());

        int sent = notificationDispatchService.flushCategoryRegionSummaries();

        assertThat(sent).isEqualTo(1);
        verify(solapiSmsClient).send(eq("010-1111-1111"), eq("땡 잡을 기회 1건이 생겼어요!"));
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isEqualTo(NOW);
    }

    @Test
    @DisplayName("60분이 지나도 빈 슬롯이 없으면 문자를 보내지 않고 목록만 비운다")
    void flushCategoryRegionSummaries_noOpenSlot_clearsListAndKeepsLastSentAt() {
        NotificationSubscription subscription = notificationSubscriptionRepository.save(
                categoryRegion(USER_ID, NOW.plusHours(6)));
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        event.markProcessed();
        notificationEventRepository.saveAndFlush(event);
        categoryRegionPendingQueue.markSent(subscription.getId(), NOW.minusMinutes(60));
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), event.getId());

        int sent = notificationDispatchService.flushCategoryRegionSummaries();

        assertThat(sent).isZero();
        verify(solapiSmsClient, never()).send(anyString(), anyString());
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).isEmpty();
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isEqualTo(NOW.minusMinutes(60));
    }

    @Test
    @DisplayName("묶음으로 보낼 슬롯이 없으면 다음 빈자리는 다시 단건 발송 줄로 나간다")
    void flushCategoryRegionSummaries_noOpenSlot_nextEventCreatesSingleDispatch() {
        NotificationSubscription subscription = notificationSubscriptionRepository.save(
                categoryRegion(USER_ID, NOW.plusHours(6)));
        NotificationEvent closed = notificationEventRepository.saveAndFlush(event(1L));
        closed.markProcessed();
        notificationEventRepository.saveAndFlush(closed);
        categoryRegionPendingQueue.markSent(subscription.getId(), NOW.minusMinutes(60));
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), closed.getId());
        notificationDispatchService.flushCategoryRegionSummaries();

        openSlot(2L, SLOT_START.plusHours(1), SLOT_START.plusHours(2));
        notificationEventRepository.saveAndFlush(event(2L));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(1);
        assertThat(notificationDispatchRepository.count()).isEqualTo(1);
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).isEmpty();
    }

    @Test
    @DisplayName("마지막 발송 시각부터 59분이면 묶음 문자를 보내지 않는다")
    void flushCategoryRegionSummaries_within59Minutes_doesNothing() {
        NotificationSubscription subscription = notificationSubscriptionRepository.save(
                categoryRegion(USER_ID, NOW.plusHours(6)));
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        categoryRegionPendingQueue.markSent(subscription.getId(), NOW.minusMinutes(59));
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), event.getId());

        int sent = notificationDispatchService.flushCategoryRegionSummaries();

        assertThat(sent).isZero();
        verify(solapiSmsClient, never()).send(anyString(), anyString());
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).containsExactly(event.getId());
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isEqualTo(NOW.minusMinutes(59));
    }

    @Test
    @DisplayName("해제한 카테고리 구독은 묶음 문자를 보내지 않고 목록만 비운다")
    void flushCategoryRegionSummaries_inactiveSubscription_clearsWithoutSending() {
        NotificationSubscription subscription = categoryRegion(USER_ID, NOW.plusHours(6));
        subscription.deactivate();
        subscription = notificationSubscriptionRepository.save(subscription);
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        categoryRegionPendingQueue.markSent(subscription.getId(), NOW.minusMinutes(60));
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), event.getId());

        int sent = notificationDispatchService.flushCategoryRegionSummaries();

        assertThat(sent).isZero();
        verify(solapiSmsClient, never()).send(anyString(), anyString());
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).isEmpty();
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isEqualTo(NOW.minusMinutes(60));
    }

    @Test
    @DisplayName("해제한 카테고리 구독은 새 빈자리를 발송 줄에도 메모장에도 넣지 않는다")
    void createDispatchJobs_inactiveCategory_createsNothing() {
        NotificationSubscription subscription = categoryRegion(USER_ID, NOW.plusHours(6));
        subscription.deactivate();
        subscription = notificationSubscriptionRepository.save(subscription);
        notificationEventRepository.saveAndFlush(event(1L));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(0);
        assertThat(notificationDispatchRepository.count()).isZero();
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).isEmpty();
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isNull();
        verify(solapiSmsClient, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("카테고리 발송 줄이 생긴 뒤 구독을 해제하면 문자를 보내지 않는다")
    void recheckClaimedJob_inactiveCategory_skipsSend() {
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        NotificationSubscription subscription = notificationSubscriptionRepository.save(
                categoryRegion(USER_ID, NOW.plusHours(6)));
        notificationEventRepository.saveAndFlush(event(1L));
        notificationDispatchService.createDispatchJobs();
        subscription.deactivate();
        notificationSubscriptionRepository.saveAndFlush(subscription);

        NotificationDispatch job = notificationDispatchService.claimDispatchJob().orElseThrow();
        boolean canSend = notificationDispatchService.recheckClaimedJob(job);

        assertThat(canSend).isFalse();
        assertThat(job.getStatus()).isEqualTo(NotificationDispatchStatus.SKIPPED);
        verify(solapiSmsClient, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("전화번호가 없으면 묶음 문자를 보내지 않고 목록을 남겨 둔다")
    void flushCategoryRegionSummaries_missingPhone_keepsPendingEvents() {
        NotificationSubscription subscription = notificationSubscriptionRepository.save(
                categoryRegion(USER_ID, NOW.plusHours(6)));
        jdbcTemplate.update("UPDATE APP_USER SET PHONE = ' ' WHERE USER_ID = ?", USER_ID);
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        categoryRegionPendingQueue.markSent(subscription.getId(), NOW.minusMinutes(60));
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), event.getId());

        int sent = notificationDispatchService.flushCategoryRegionSummaries();

        assertThat(sent).isZero();
        verify(solapiSmsClient, never()).send(anyString(), anyString());
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).containsExactly(event.getId());
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isEqualTo(NOW.minusMinutes(60));
    }

    @Test
    @DisplayName("묶음 문자 발송이 실패해도 목록을 비우고 마지막 발송 시각을 갱신한다")
    void flushCategoryRegionSummaries_sendFailed_clearsListAndMarksSent() {
        NotificationSubscription subscription = notificationSubscriptionRepository.save(
                categoryRegion(USER_ID, NOW.plusHours(6)));
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        categoryRegionPendingQueue.markSent(subscription.getId(), NOW.minusMinutes(60));
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), event.getId());
        given(solapiSmsClient.send(anyString(), anyString())).willReturn(SmsSendResult.failed("발송 실패"));

        int sent = notificationDispatchService.flushCategoryRegionSummaries();

        assertThat(sent).isEqualTo(1);
        assertThat(notificationDispatchRepository.count()).isZero();
        assertThat(notificationInboxRepository.count()).isZero();
        assertThat(categoryRegionPendingQueue.pendingEventIds(subscription.getId())).isEmpty();
        assertThat(categoryRegionPendingQueue.lastSentAt(subscription.getId())).isEqualTo(NOW);
    }

    @Test
    @DisplayName("매장 구독은 임박·초임박·재공개를 각각 한 통씩 보내고 메모장에는 넣지 않는다")
    void storeSubscription_eachEventType_sendsOneSms() {
        notificationSubscriptionRepository.save(store(USER_ID));
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        openSlot(2L, NOW.plusHours(2), NOW.plusHours(3));
        notificationEventRepository.saveAndFlush(typedEvent(
                NotificationEventType.IMMINENT, 1L, null, SLOT_START));
        notificationEventRepository.saveAndFlush(typedEvent(
                NotificationEventType.LAST_MINUTE, 2L, null, NOW.plusHours(2)));
        prepareReopenedSlot(NOW.plusHours(4));
        notificationEventRepository.saveAndFlush(typedEvent(
                NotificationEventType.REOPENED, null, 500L, NOW.plusHours(4)));

        assertThat(notificationDispatchService.createDispatchJobs()).contains(1);
        assertThat(notificationDispatchService.createDispatchJobs()).contains(1);
        assertThat(notificationDispatchService.createDispatchJobs()).contains(1);

        sendOneClaimedJob();
        sendOneClaimedJob();
        sendOneClaimedJob();

        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getStatus)
                .containsOnly(NotificationDispatchStatus.SENT)
                .hasSize(3);
        verify(solapiSmsClient, times(3)).send(eq("010-1111-1111"), anyString());
        assertThat(categoryRegionPendingQueue.subscriptionIds()).isEmpty();
    }

    @Test
    @DisplayName("묶음 건수는 예약이 들어간 슬롯을 빼고 아직 빈 슬롯만 센다")
    void flushCategoryRegionSummaries_reservedSlot_isExcludedFromCount() {
        NotificationSubscription subscription = notificationSubscriptionRepository.save(
                categoryRegion(USER_ID, NOW.plusHours(6)));
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        openSlot(2L, SLOT_START.plusHours(1), SLOT_START.plusHours(2));
        NotificationFixture.treatment(jdbcTemplate, 100L, SHOP_ID, "NORMAL");
        NotificationFixture.reservation(jdbcTemplate, 500L, USER_ID, 100L, "CONFIRMED",
                SLOT_START, SLOT_START.plusHours(1), NOW.plusMinutes(5));
        NotificationEvent reserved = notificationEventRepository.saveAndFlush(event(1L));
        NotificationEvent open = notificationEventRepository.saveAndFlush(event(2L));
        categoryRegionPendingQueue.markSent(subscription.getId(), NOW.minusMinutes(60));
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), reserved.getId());
        categoryRegionPendingQueue.addPendingEvent(subscription.getId(), open.getId());

        int sent = notificationDispatchService.flushCategoryRegionSummaries();

        assertThat(sent).isEqualTo(1);
        assertThat(notificationDispatchRepository.count()).isZero();
        verify(solapiSmsClient).send(eq("010-1111-1111"), eq("땡 잡을 기회 1건이 생겼어요!"));
    }

    @Test
    @DisplayName("카테고리 구독이 두 개면 60분 안에는 발송 줄을 만들지 않고 메모장도 두 개로 쌓는다")
    void createDispatchJobs_twoCategorySubscriptions_keepSeparatePendingLists() {
        NotificationSubscription first = notificationSubscriptionRepository.save(
                categoryRegion(USER_ID, NOW.plusHours(6)));
        NotificationSubscription second = notificationSubscriptionRepository.save(
                categoryRegion(OTHER_USER_ID, NOW.plusHours(6)));
        notificationEventRepository.saveAndFlush(event(1L));

        assertThat(notificationDispatchService.createDispatchJobs()).contains(2);
        assertThat(categoryRegionPendingQueue.lastSentAt(first.getId())).isEqualTo(NOW);
        assertThat(categoryRegionPendingQueue.lastSentAt(second.getId())).isEqualTo(NOW);
        assertThat(categoryRegionPendingQueue.pendingEventIds(first.getId())).isEmpty();
        assertThat(categoryRegionPendingQueue.pendingEventIds(second.getId())).isEmpty();

        NotificationEvent later = notificationEventRepository.saveAndFlush(event(2L));
        assertThat(notificationDispatchService.createDispatchJobs()).contains(0);

        assertThat(notificationDispatchRepository.count()).isEqualTo(2);
        assertThat(categoryRegionPendingQueue.pendingEventIds(first.getId())).containsExactly(later.getId());
        assertThat(categoryRegionPendingQueue.pendingEventIds(second.getId())).containsExactly(later.getId());

        categoryRegionPendingQueue.clearPendingEvents(first.getId());
        assertThat(categoryRegionPendingQueue.pendingEventIds(first.getId())).isEmpty();
        assertThat(categoryRegionPendingQueue.pendingEventIds(second.getId())).containsExactly(later.getId());
    }

    @Test
    @DisplayName("대기 작업을 처리 중으로 바꾼 뒤에만 그 작업을 가져간다")
    void claimDispatchJob_pendingJob_becomesProcessing() {
        NotificationDispatch job = pendingJob(1L, USER_ID);

        Optional<NotificationDispatch> claimed = notificationDispatchService.claimDispatchJob();

        assertThat(claimed).isPresent();
        assertThat(claimed.get().getId()).isEqualTo(job.getId());
        assertThat(claimed.get().getStatus()).isEqualTo(NotificationDispatchStatus.PROCESSING);
    }

    @Test
    @DisplayName("다른 프로세스가 먼저 처리 중으로 바꿨으면 수정 건수가 0이라 넘어간다")
    void claimDispatchJob_alreadyClaimed_returnsEmpty() {
        pendingJob(1L, USER_ID);
        notificationDispatchService.claimDispatchJob();

        Optional<NotificationDispatch> claimed = notificationDispatchService.claimDispatchJob();

        assertThat(claimed).isEmpty();
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getStatus)
                .containsExactly(NotificationDispatchStatus.PROCESSING);
    }

    @Test
    @DisplayName("한 번 호출에는 ID가 작은 대기 작업 하나만 가져간다")
    void claimDispatchJob_twoPendingJobs_claimsOlderOne() {
        NotificationEvent savedEvent = notificationEventRepository.saveAndFlush(event(1L));
        NotificationDispatch older = notificationDispatchRepository.saveAndFlush(dispatch(savedEvent.getId(), USER_ID));
        NotificationDispatch newer = notificationDispatchRepository.saveAndFlush(
                dispatch(savedEvent.getId(), OTHER_USER_ID));

        Optional<NotificationDispatch> claimed = notificationDispatchService.claimDispatchJob();

        assertThat(claimed).get().extracting(NotificationDispatch::getId).isEqualTo(older.getId());
        assertThat(notificationDispatchRepository.findById(newer.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationDispatchStatus.PENDING);
    }

    @Test
    @DisplayName("대기 작업이 없으면 가져갈 작업이 없다")
    void claimDispatchJob_noPendingJob_returnsEmpty() {
        assertThat(notificationDispatchService.claimDispatchJob()).isEmpty();
    }

    @Test
    @DisplayName("구독이 활성이고 희망 종료가 남았고 빈자리가 아직 팔리면 그대로 둔다")
    void recheckClaimedJob_stillSendable_keepsProcessing() {
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        notificationSubscriptionRepository.save(store(USER_ID));
        NotificationDispatch job = claimedJob(1L, USER_ID);

        boolean canSend = notificationDispatchService.recheckClaimedJob(job);

        assertThat(canSend).isTrue();
        assertThat(job.getStatus()).isEqualTo(NotificationDispatchStatus.PROCESSING);
    }

    @Test
    @DisplayName("구독을 해제했으면 끝으로 두고 보내지 않는다")
    void recheckClaimedJob_inactiveSubscription_skips() {
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        NotificationSubscription subscription = store(USER_ID);
        subscription.deactivate();
        notificationSubscriptionRepository.save(subscription);
        NotificationDispatch job = claimedJob(1L, USER_ID);

        boolean canSend = notificationDispatchService.recheckClaimedJob(job);

        assertThat(canSend).isFalse();
        assertThat(job.getStatus()).isEqualTo(NotificationDispatchStatus.SKIPPED);
    }

    @Test
    @DisplayName("희망 종료가 지났으면 끝으로 두고 보내지 않는다")
    void recheckClaimedJob_hopeEnded_skips() {
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        notificationSubscriptionRepository.save(NotificationSubscription.builder()
                .userId(USER_ID)
                .subscriptionType(SubscriptionType.STORE)
                .shopId(SHOP_ID)
                .startTime(NOW.minusHours(5))
                .endTime(NOW.minusHours(1))
                .build());
        NotificationDispatch job = claimedJob(1L, USER_ID);

        boolean canSend = notificationDispatchService.recheckClaimedJob(job);

        assertThat(canSend).isFalse();
        assertThat(job.getStatus()).isEqualTo(NotificationDispatchStatus.SKIPPED);
    }

    @Test
    @DisplayName("빈자리를 다른 예약이 잡았으면 끝으로 두고 보내지 않는다")
    void recheckClaimedJob_slotOccupied_skips() {
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        NotificationFixture.treatment(jdbcTemplate, 100L, SHOP_ID, "NORMAL");
        NotificationFixture.reservation(jdbcTemplate, 500L, USER_ID, 100L, "CONFIRMED",
                SLOT_START, SLOT_START.plusHours(1), NOW.plusMinutes(5));
        notificationSubscriptionRepository.save(store(USER_ID));
        NotificationDispatch job = claimedJob(1L, USER_ID);

        boolean canSend = notificationDispatchService.recheckClaimedJob(job);

        assertThat(canSend).isFalse();
        assertThat(job.getStatus()).isEqualTo(NotificationDispatchStatus.SKIPPED);
    }

    @Test
    @DisplayName("선점하지 않은 대기 작업은 다시 보지 않는다")
    void recheckClaimedJob_pendingJob_isLeftPending() {
        NotificationDispatch job = pendingJob(1L, USER_ID);

        boolean canSend = notificationDispatchService.recheckClaimedJob(job);

        assertThat(canSend).isFalse();
        assertThat(job.getStatus()).isEqualTo(NotificationDispatchStatus.PENDING);
    }

    @Test
    @DisplayName("취소된 예약 시간이 아직 팔리면 재공개 작업은 그대로 둔다")
    void recheckClaimedJob_reopenedStillSellable_keepsProcessing() {
        LocalDateTime startsAt = NOW.plusHours(3);
        openSlot(1L, NOW.plusHours(2), NOW.plusHours(6));
        NotificationFixture.treatment(jdbcTemplate, 100L, SHOP_ID, "NORMAL");
        NotificationFixture.reservation(jdbcTemplate, 500L, USER_ID, 100L, "CANCELLED",
                startsAt, startsAt.plusHours(1), startsAt.minusHours(1));
        notificationSubscriptionRepository.save(storeUntil(USER_ID, NOW.plusHours(5)));
        NotificationDispatch job = claimedReopenedJob(startsAt);

        boolean canSend = notificationDispatchService.recheckClaimedJob(job);

        assertThat(canSend).isTrue();
        assertThat(job.getStatus()).isEqualTo(NotificationDispatchStatus.PROCESSING);
    }

    @Test
    @DisplayName("OPEN 행이 없어도 영업시간 안이면 재공개 작업은 그대로 둔다")
    void recheckClaimedJob_reopenedBusinessHoursOnly_keepsProcessing() {
        LocalDateTime startsAt = NOW.plusHours(3);
        NotificationFixture.businessHour(jdbcTemplate, 1L, SHOP_ID, "THU", "10:00", "20:00");
        NotificationFixture.treatment(jdbcTemplate, 100L, SHOP_ID, "NORMAL");
        NotificationFixture.reservation(jdbcTemplate, 500L, USER_ID, 100L, "CANCELLED",
                startsAt, startsAt.plusHours(1), startsAt.minusHours(1));
        notificationSubscriptionRepository.save(storeUntil(USER_ID, NOW.plusHours(5)));
        NotificationDispatch job = claimedReopenedJob(startsAt);

        boolean canSend = notificationDispatchService.recheckClaimedJob(job);

        assertThat(canSend).isTrue();
        assertThat(job.getStatus()).isEqualTo(NotificationDispatchStatus.PROCESSING);
    }

    @Test
    @DisplayName("취소된 예약 시간을 닫았으면 재공개 작업은 끝으로 두고 보내지 않는다")
    void recheckClaimedJob_reopenedClosed_skips() {
        LocalDateTime startsAt = NOW.plusHours(3);
        openSlot(1L, NOW.plusHours(2), NOW.plusHours(6));
        NotificationFixture.treatment(jdbcTemplate, 100L, SHOP_ID, "NORMAL");
        NotificationFixture.reservation(jdbcTemplate, 500L, USER_ID, 100L, "CANCELLED",
                startsAt, startsAt.plusHours(1), startsAt.minusHours(1));
        NotificationFixture.exception(jdbcTemplate, 2L, SHOP_ID, 500L, "CLOSE", startsAt, startsAt.plusHours(1));
        notificationSubscriptionRepository.save(storeUntil(USER_ID, NOW.plusHours(5)));
        NotificationDispatch job = claimedReopenedJob(startsAt);

        boolean canSend = notificationDispatchService.recheckClaimedJob(job);

        assertThat(canSend).isFalse();
        assertThat(job.getStatus()).isEqualTo(NotificationDispatchStatus.SKIPPED);
    }

    @Test
    @DisplayName("매장이 비활성이면 재공개 작업은 끝으로 두고 보내지 않는다")
    void recheckClaimedJob_reopenedInactiveShop_skips() {
        LocalDateTime startsAt = NOW.plusHours(3);
        openSlot(1L, NOW.plusHours(2), NOW.plusHours(6));
        NotificationFixture.treatment(jdbcTemplate, 100L, SHOP_ID, "NORMAL");
        NotificationFixture.reservation(jdbcTemplate, 500L, USER_ID, 100L, "CANCELLED",
                startsAt, startsAt.plusHours(1), startsAt.minusHours(1));
        notificationSubscriptionRepository.save(storeUntil(USER_ID, NOW.plusHours(5)));
        NotificationDispatch job = claimedReopenedJob(startsAt);
        jdbcTemplate.update("UPDATE SHOP SET STATUS = 'INACTIVE' WHERE SHOP_ID = ?", SHOP_ID);

        boolean canSend = notificationDispatchService.recheckClaimedJob(job);

        assertThat(canSend).isFalse();
        assertThat(job.getStatus()).isEqualTo(NotificationDispatchStatus.SKIPPED);
    }

    @Test
    @DisplayName("재검사를 통과한 작업에 문자를 보내면 SENT 로 남긴다")
    void sendClaimedJob_success_marksSent() {
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        notificationSubscriptionRepository.save(store(USER_ID));
        NotificationDispatch job = claimedJob(1L, USER_ID);

        notificationDispatchService.sendClaimedJob(job);

        NotificationDispatch saved = notificationDispatchRepository.findById(job.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NotificationDispatchStatus.SENT);
        assertThat(saved.getFailReason()).isNull();
        // 본문 시각은 연도 포함 한국어 형식이다. 기본 toString(2026-10-22T15:00)을 쓰지 않는다.
        verify(solapiSmsClient).send(anyString(), contains("2026년 10월 22일 15:00"));
        NotificationInbox inbox = notificationInboxRepository.findAll().get(0);
        assertThat(inbox.getUserId()).isEqualTo(USER_ID);
        assertThat(inbox.getCategory()).isEqualTo(NotificationInboxCategory.INTEREST);
        assertThat(inbox.getTitle()).isEqualTo("임박 알림");
        assertThat(inbox.getBody()).isEqualTo("임박! 매장1-2026년 10월 22일 15:00 땡잡을 기회가 생겼어요!");
        assertThat(inbox.getLinkPath()).isEqualTo("/shops/1");
        assertThat(inbox.getReadAt()).isNull();
    }

    @Test
    @DisplayName("회원 전화번호가 없으면 FAILED 로 남긴다")
    void sendClaimedJob_noPhone_marksFailed() {
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        notificationSubscriptionRepository.save(store(USER_ID));
        NotificationDispatch job = claimedJob(1L, USER_ID);
        jdbcTemplate.update("UPDATE APP_USER SET PHONE = ' ' WHERE USER_ID = ?", USER_ID);

        notificationDispatchService.sendClaimedJob(job);

        NotificationDispatch saved = notificationDispatchRepository.findById(job.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NotificationDispatchStatus.FAILED);
        assertThat(saved.getFailReason()).contains("전화번호");
        assertThat(notificationInboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("문자 API가 실패하면 FAILED 로 남긴다")
    void sendClaimedJob_smsFailed_marksFailed() {
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        notificationSubscriptionRepository.save(store(USER_ID));
        NotificationDispatch job = claimedJob(1L, USER_ID);
        given(solapiSmsClient.send(anyString(), anyString()))
                .willReturn(SmsSendResult.failed("발송 거절"));

        notificationDispatchService.sendClaimedJob(job);

        NotificationDispatch saved = notificationDispatchRepository.findById(job.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NotificationDispatchStatus.FAILED);
        assertThat(saved.getFailReason()).isEqualTo("발송 거절");
        assertThat(notificationInboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("문자 API 응답이 애매하면 UNKNOWN 으로 남긴다")
    void sendClaimedJob_smsUnknown_marksUnknown() {
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        notificationSubscriptionRepository.save(store(USER_ID));
        NotificationDispatch job = claimedJob(1L, USER_ID);
        given(solapiSmsClient.send(anyString(), anyString()))
                .willReturn(SmsSendResult.unknown("응답 없음"));

        notificationDispatchService.sendClaimedJob(job);

        NotificationDispatch saved = notificationDispatchRepository.findById(job.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NotificationDispatchStatus.UNKNOWN);
        assertThat(saved.getFailReason()).isEqualTo("응답 없음");
        assertThat(notificationInboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("선점하지 않은 대기 작업에는 문자를 보내지 않는다")
    void sendClaimedJob_pendingJob_isLeftPending() {
        NotificationDispatch job = pendingJob(1L, USER_ID);

        notificationDispatchService.sendClaimedJob(job);

        assertThat(job.getStatus()).isEqualTo(NotificationDispatchStatus.PENDING);
        verify(solapiSmsClient, times(0)).send(anyString(), anyString());
    }

    @Test
    @DisplayName("이미 SENT 이면 문자를 다시 보내지 않는다")
    void sendClaimedJob_alreadySent_doesNotResend() {
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        notificationSubscriptionRepository.save(store(USER_ID));
        NotificationDispatch job = claimedJob(1L, USER_ID);

        notificationDispatchService.sendClaimedJob(job);
        notificationDispatchService.sendClaimedJob(job);

        NotificationDispatch saved = notificationDispatchRepository.findById(job.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NotificationDispatchStatus.SENT);
        verify(solapiSmsClient, times(1)).send(anyString(), anyString());
    }

    @Test
    @DisplayName("이미 UNKNOWN 이면 문자를 다시 보내지 않는다")
    void sendClaimedJob_alreadyUnknown_doesNotResend() {
        openSlot(1L, SLOT_START, SLOT_START.plusHours(1));
        notificationSubscriptionRepository.save(store(USER_ID));
        NotificationDispatch job = claimedJob(1L, USER_ID);
        given(solapiSmsClient.send(anyString(), anyString()))
                .willReturn(SmsSendResult.unknown("응답 없음"));

        notificationDispatchService.sendClaimedJob(job);
        notificationDispatchService.sendClaimedJob(job);

        NotificationDispatch saved = notificationDispatchRepository.findById(job.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NotificationDispatchStatus.UNKNOWN);
        verify(solapiSmsClient, times(1)).send(anyString(), anyString());
    }

    @Test
    @DisplayName("같은 이벤트·회원에 SENT 가 있으면 대기 작업을 새로 만들지 않는다")
    void createDispatchJobs_alreadySent_skipsUser() {
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        NotificationDispatch existing = notificationDispatchRepository.saveAndFlush(
                NotificationDispatch.builder().eventId(event.getId()).userId(USER_ID).build());
        existing.markSent();
        notificationDispatchRepository.saveAndFlush(existing);
        notificationSubscriptionRepository.save(store(USER_ID));

        Optional<Integer> created = notificationDispatchService.createDispatchJobs();

        assertThat(created).contains(0);
        assertThat(notificationDispatchRepository.count()).isEqualTo(1);
        assertThat(notificationDispatchRepository.findById(existing.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationDispatchStatus.SENT);
    }

    private void sendOneClaimedJob() {
        NotificationDispatch job = notificationDispatchService.claimDispatchJob().orElseThrow();
        assertThat(notificationDispatchService.recheckClaimedJob(job)).isTrue();
        notificationDispatchService.sendClaimedJob(job);
    }

    private void prepareReopenedSlot(LocalDateTime startsAt) {
        NotificationFixture.businessHour(jdbcTemplate, 1L, SHOP_ID, "THU", "10:00", "20:00");
        NotificationFixture.treatment(jdbcTemplate, 100L, SHOP_ID, "NORMAL");
        NotificationFixture.reservation(jdbcTemplate, 500L, OTHER_USER_ID, 100L, "CANCELLED",
                startsAt, startsAt.plusHours(1), startsAt.minusHours(1));
    }

    private static NotificationEvent typedEvent(NotificationEventType eventType, Long exceptionId,
            Long reservationId, LocalDateTime startsAt) {
        return NotificationEvent.builder()
                .eventType(eventType)
                .exceptionId(exceptionId)
                .shopId(SHOP_ID)
                .reservationId(reservationId)
                .slotStartsAt(startsAt)
                .slotEndsAt(startsAt.plusHours(1))
                .build();
    }

    private NotificationDispatch claimedJob(long exceptionId, long userId) {
        pendingJob(exceptionId, userId);
        return notificationDispatchService.claimDispatchJob().orElseThrow();
    }

    private NotificationDispatch claimedReopenedJob(LocalDateTime startsAt) {
        // 재공개는 OPEN 행이 없을 수 있어 exceptionId 를 비운다.
        NotificationEvent savedEvent = notificationEventRepository.saveAndFlush(NotificationEvent.builder()
                .eventType(NotificationEventType.REOPENED)
                .exceptionId(null)
                .shopId(SHOP_ID)
                .reservationId(500L)
                .slotStartsAt(startsAt)
                .slotEndsAt(startsAt.plusHours(1))
                .build());
        notificationDispatchRepository.saveAndFlush(dispatch(savedEvent.getId(), USER_ID));
        return notificationDispatchService.claimDispatchJob().orElseThrow();
    }

    private void openSlot(long exceptionId, LocalDateTime startsAt, LocalDateTime endsAt) {
        NotificationFixture.exception(jdbcTemplate, exceptionId, SHOP_ID, null, "OPEN", startsAt, endsAt);
    }

    private static NotificationSubscription storeUntil(long userId, LocalDateTime endTime) {
        return NotificationSubscription.builder()
                .userId(userId)
                .subscriptionType(SubscriptionType.STORE)
                .shopId(SHOP_ID)
                .startTime(NOW)
                .endTime(endTime)
                .build();
    }

    private NotificationDispatch pendingJob(long exceptionId, long userId) {
        NotificationEvent savedEvent = notificationEventRepository.saveAndFlush(event(exceptionId));
        return notificationDispatchRepository.saveAndFlush(dispatch(savedEvent.getId(), userId));
    }

    private static NotificationDispatch dispatch(long eventId, long userId) {
        return NotificationDispatch.builder()
                .eventId(eventId)
                .userId(userId)
                .build();
    }

    private static NotificationEvent event(long exceptionId) {
        // 유니크가 매장+종류+시작 시각이라, 이벤트마다 시작 시각을 다르게 둔다.
        LocalDateTime startsAt = SLOT_START.plusHours(exceptionId - 1);
        return NotificationEvent.builder()
                .eventType(NotificationEventType.IMMINENT)
                .exceptionId(exceptionId)
                .shopId(SHOP_ID)
                .slotStartsAt(startsAt)
                .slotEndsAt(startsAt.plusHours(1))
                .build();
    }

    private static NotificationSubscription store(long userId) {
        return NotificationSubscription.builder()
                .userId(userId)
                .subscriptionType(SubscriptionType.STORE)
                .shopId(SHOP_ID)
                .startTime(NOW)
                .endTime(NOW.plusHours(5))
                .build();
    }

    private static NotificationSubscription categoryRegion(long userId, LocalDateTime endTime) {
        return NotificationSubscription.builder()
                .userId(userId)
                .subscriptionType(SubscriptionType.CATEGORY_REGION)
                .categoryId(10L)
                .regionCode("SEOUL_GANGNAM")
                .startTime(NOW)
                .endTime(endTime)
                .build();
    }

    /** 희망 종료 비교에 쓰는 현재 시각을 NOW 로 고정한다. */
    @TestConfiguration
    @EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);
        }

        @Bean
        DateTimeProvider auditingDateTimeProvider(Clock clock) {
            return () -> Optional.of(LocalDateTime.now(clock));
        }
    }
}
