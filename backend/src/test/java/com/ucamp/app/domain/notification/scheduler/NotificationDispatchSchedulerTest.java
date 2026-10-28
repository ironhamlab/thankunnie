package com.ucamp.app.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.notification.NotificationFixture;
import com.ucamp.app.domain.notification.client.FakeSolapiSmsClientConfig;
import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.entity.NotificationDispatchStatus;
import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventStatus;
import com.ucamp.app.domain.notification.entity.NotificationEventType;
import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionType;
import com.ucamp.app.domain.notification.repository.NotificationDispatchRepository;
import com.ucamp.app.domain.notification.repository.NotificationEventRepository;
import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.ShopCategoryRegionRepository;
import com.ucamp.app.domain.notification.repository.VacancySlotRepository;
import com.ucamp.app.domain.notification.service.NotificationDispatchService;
import com.ucamp.app.domain.notification.service.SubscriptionMatchService;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationDispatchScheduler.class, NotificationDispatchService.class, SubscriptionMatchService.class,
        ShopCategoryRegionRepository.class, VacancySlotRepository.class, RecipientContactRepository.class,
        FakeSolapiSmsClientConfig.class, NotificationDispatchSchedulerTest.FixedClock.class})
@ActiveProfiles("test")
class NotificationDispatchSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);
    private static final LocalDateTime SLOT_START = NOW.plusHours(3);
    private static final long SHOP_ID = 1L;
    private static final long USER_ID = 1L;

    @Autowired
    private NotificationDispatchScheduler scheduler;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private NotificationDispatchRepository notificationDispatchRepository;

    @Autowired
    private NotificationSubscriptionRepository notificationSubscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.shop(jdbcTemplate, SHOP_ID, 10L, "SEOUL_GANGNAM", "ACTIVE");
        NotificationFixture.user(jdbcTemplate, USER_ID);
    }

    @Test
    @DisplayName("저장 스케줄은 대기 작업만 만들고 선점하지 않는다")
    void saveDispatchJobs_createsPendingJob() {
        NotificationEvent event = saveEvent();
        notificationSubscriptionRepository.save(store(USER_ID));

        scheduler.saveDispatchJobs();

        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getStatus)
                .containsExactly(NotificationDispatchStatus.PENDING);
    }

    @Test
    @DisplayName("저장 스케줄은 PENDING 이벤트를 한 번에 모두 대기 작업으로 만든다")
    void saveDispatchJobs_twoPendingEvents_createsBoth() {
        notificationSubscriptionRepository.save(store(USER_ID));
        NotificationEvent first = saveEvent(1L, SHOP_ID);
        NotificationEvent second = saveEvent(2L, SHOP_ID);

        scheduler.saveDispatchJobs();

        assertThat(first.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(second.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getEventId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    @DisplayName("맞는 회원이 없는 이벤트가 있어도 다음 PENDING 이벤트를 계속 쪽지로 만든다")
    void saveDispatchJobs_noRecipientThenAnother_processesBoth() {
        NotificationFixture.shop(jdbcTemplate, 2L, 10L, "SEOUL_MAPO", "ACTIVE");
        NotificationEvent noRecipient = saveEvent(2L, 2L);
        NotificationEvent withRecipient = saveEvent(1L, SHOP_ID);
        notificationSubscriptionRepository.save(store(USER_ID));

        scheduler.saveDispatchJobs();

        assertThat(noRecipient.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(withRecipient.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getEventId, NotificationDispatch::getUserId)
                .containsExactly(tuple(withRecipient.getId(), USER_ID));
    }

    @Test
    @DisplayName("선점 스케줄은 재검사를 통과한 작업에 문자를 보내고 SENT 로 남긴다")
    void claimAndRecheck_stillSendable_marksSent() {
        NotificationFixture.exception(jdbcTemplate, 1L, SHOP_ID, null, "OPEN", SLOT_START, SLOT_START.plusHours(1));
        notificationSubscriptionRepository.save(store(USER_ID));
        saveEvent();
        scheduler.saveDispatchJobs();

        scheduler.claimAndRecheck();

        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getStatus)
                .containsExactly(NotificationDispatchStatus.SENT);
    }

    @Test
    @DisplayName("선점 스케줄은 대기 작업을 한 번에 모두 집고 문자를 보낸다")
    void claimAndRecheck_twoPendingJobs_sendsBoth() {
        NotificationFixture.user(jdbcTemplate, 2L);
        NotificationFixture.exception(jdbcTemplate, 1L, SHOP_ID, null, "OPEN", SLOT_START, SLOT_START.plusHours(1));
        notificationSubscriptionRepository.save(store(USER_ID));
        notificationSubscriptionRepository.save(store(2L));
        saveEvent();
        scheduler.saveDispatchJobs();

        scheduler.claimAndRecheck();

        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getUserId, NotificationDispatch::getStatus)
                .containsExactlyInAnyOrder(
                        tuple(USER_ID, NotificationDispatchStatus.SENT),
                        tuple(2L, NotificationDispatchStatus.SENT));
    }

    @Test
    @DisplayName("이미 처리 중인 쪽지는 다시 집지 않고 대기 중인 쪽지만 보내 SENT 로 남긴다")
    void claimAndRecheck_processingJob_leavesItAndSendsPending() {
        NotificationFixture.user(jdbcTemplate, 2L);
        NotificationFixture.exception(jdbcTemplate, 1L, SHOP_ID, null, "OPEN", SLOT_START, SLOT_START.plusHours(1));
        NotificationSubscription minji = notificationSubscriptionRepository.save(store(USER_ID));
        NotificationSubscription sua = notificationSubscriptionRepository.save(store(2L));
        saveEvent();
        scheduler.saveDispatchJobs();

        NotificationDispatch alreadyProcessing = notificationDispatchService.claimDispatchJob().orElseThrow();
        // 이미 집은 사람의 구독을 해제한다. 그 쪽지를 다시 보면 SKIPPED가 된다.
        // 다시 보지 않으면 PROCESSING으로 남고, 아직 대기인 사람만 집어서 보낸다.
        if (alreadyProcessing.getUserId().equals(USER_ID)) {
            minji.deactivate();
        } else {
            sua.deactivate();
        }
        Long alreadyUserId = alreadyProcessing.getUserId();
        Long otherUserId = alreadyUserId.equals(USER_ID) ? 2L : USER_ID;

        scheduler.claimAndRecheck();

        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getUserId, NotificationDispatch::getStatus)
                .containsExactlyInAnyOrder(
                        tuple(alreadyUserId, NotificationDispatchStatus.PROCESSING),
                        tuple(otherUserId, NotificationDispatchStatus.SENT));
    }

    @Test
    @DisplayName("한 명이 재검사에 걸려도 다른 대기 작업은 계속 보내 SENT 로 남긴다")
    void claimAndRecheck_oneInactive_sendsTheOther() {
        NotificationFixture.user(jdbcTemplate, 2L);
        NotificationFixture.exception(jdbcTemplate, 1L, SHOP_ID, null, "OPEN", SLOT_START, SLOT_START.plusHours(1));
        NotificationSubscription inactive = notificationSubscriptionRepository.save(store(USER_ID));
        notificationSubscriptionRepository.save(store(2L));
        saveEvent();
        scheduler.saveDispatchJobs();
        inactive.deactivate();

        scheduler.claimAndRecheck();

        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getUserId, NotificationDispatch::getStatus)
                .containsExactlyInAnyOrder(
                        tuple(USER_ID, NotificationDispatchStatus.SKIPPED),
                        tuple(2L, NotificationDispatchStatus.SENT));
    }

    @Test
    @DisplayName("선점 스케줄은 구독이 해제된 작업을 끝으로 둔다")
    void claimAndRecheck_inactiveSubscription_skips() {
        NotificationFixture.exception(jdbcTemplate, 1L, SHOP_ID, null, "OPEN", SLOT_START, SLOT_START.plusHours(1));
        NotificationSubscription subscription = notificationSubscriptionRepository.save(store(USER_ID));
        saveEvent();
        scheduler.saveDispatchJobs();
        subscription.deactivate();

        scheduler.claimAndRecheck();

        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getStatus)
                .containsExactly(NotificationDispatchStatus.SKIPPED);
    }

    private NotificationEvent saveEvent() {
        return saveEvent(1L, SHOP_ID);
    }

    private NotificationEvent saveEvent(long exceptionId, long shopId) {
        return notificationEventRepository.saveAndFlush(NotificationEvent.builder()
                .eventType(NotificationEventType.IMMINENT)
                .exceptionId(exceptionId)
                .shopId(shopId)
                .slotStartsAt(SLOT_START)
                .slotEndsAt(SLOT_START.plusHours(1))
                .build());
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
