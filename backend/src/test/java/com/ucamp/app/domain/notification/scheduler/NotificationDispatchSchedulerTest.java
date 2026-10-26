package com.ucamp.app.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.ucamp.app.domain.notification.repository.ShopCategoryRegionRepository;
import com.ucamp.app.domain.notification.repository.VacancySlotRepository;
import com.ucamp.app.domain.notification.service.NotificationDispatchService;
import com.ucamp.app.domain.notification.service.SubscriptionMatchService;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationDispatchScheduler.class, NotificationDispatchService.class, SubscriptionMatchService.class,
        ShopCategoryRegionRepository.class, VacancySlotRepository.class,
        NotificationDispatchSchedulerTest.FixedClock.class})
@ActiveProfiles("test")
class NotificationDispatchSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);
    private static final LocalDateTime SLOT_START = NOW.plusHours(3);
    private static final long SHOP_ID = 1L;
    private static final long USER_ID = 1L;

    @Autowired
    private NotificationDispatchScheduler scheduler;

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
        notificationSubscriptionRepository.save(store());

        scheduler.saveDispatchJobs();

        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getStatus)
                .containsExactly(NotificationDispatchStatus.PENDING);
    }

    @Test
    @DisplayName("선점 스케줄은 재검사를 통과한 작업을 처리 중으로 둔다. 문자는 보내지 않는다")
    void claimAndRecheck_stillSendable_keepsProcessing() {
        NotificationFixture.exception(jdbcTemplate, 1L, SHOP_ID, null, "OPEN", SLOT_START, SLOT_START.plusHours(1));
        notificationSubscriptionRepository.save(store());
        saveEvent();
        scheduler.saveDispatchJobs();

        scheduler.claimAndRecheck();

        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getStatus)
                .containsExactly(NotificationDispatchStatus.PROCESSING);
    }

    @Test
    @DisplayName("선점 스케줄은 구독이 해제된 작업을 끝으로 둔다")
    void claimAndRecheck_inactiveSubscription_skips() {
        NotificationFixture.exception(jdbcTemplate, 1L, SHOP_ID, null, "OPEN", SLOT_START, SLOT_START.plusHours(1));
        NotificationSubscription subscription = notificationSubscriptionRepository.save(store());
        saveEvent();
        scheduler.saveDispatchJobs();
        subscription.deactivate();

        scheduler.claimAndRecheck();

        assertThat(notificationDispatchRepository.findAll())
                .extracting(NotificationDispatch::getStatus)
                .containsExactly(NotificationDispatchStatus.SKIPPED);
    }

    private NotificationEvent saveEvent() {
        return notificationEventRepository.saveAndFlush(NotificationEvent.builder()
                .eventType(NotificationEventType.IMMINENT)
                .exceptionId(1L)
                .shopId(SHOP_ID)
                .slotStartsAt(SLOT_START)
                .slotEndsAt(SLOT_START.plusHours(1))
                .build());
    }

    private static NotificationSubscription store() {
        return NotificationSubscription.builder()
                .userId(USER_ID)
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
