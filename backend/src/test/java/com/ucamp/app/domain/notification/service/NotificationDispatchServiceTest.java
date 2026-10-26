package com.ucamp.app.domain.notification.service;

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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationDispatchService.class, SubscriptionMatchService.class, ShopCategoryRegionRepository.class,
        NotificationDispatchServiceTest.FixedClock.class})
@ActiveProfiles("test")
class NotificationDispatchServiceTest {

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
    private NotificationSubscriptionRepository notificationSubscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.shop(jdbcTemplate, SHOP_ID, 10L, "SEOUL_GANGNAM", "ACTIVE");
        NotificationFixture.user(jdbcTemplate, USER_ID);
        NotificationFixture.user(jdbcTemplate, OTHER_USER_ID);
    }

    @Test
    @DisplayName("PENDING 이벤트의 맞는 회원마다 대기 작업을 넣고 이벤트를 PROCESSED로 바꾼다")
    void createDispatchJobs_matchingUsers_savesPendingJobs() {
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        notificationSubscriptionRepository.save(store(USER_ID));
        notificationSubscriptionRepository.save(store(OTHER_USER_ID));

        int created = notificationDispatchService.createDispatchJobs();

        assertThat(created).isEqualTo(2);
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

        int created = notificationDispatchService.createDispatchJobs();

        assertThat(created).isEqualTo(1);
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

        int created = notificationDispatchService.createDispatchJobs();

        assertThat(created).isEqualTo(1);
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

        int created = notificationDispatchService.createDispatchJobs();

        assertThat(created).isZero();
        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PROCESSED);
        assertThat(notificationDispatchRepository.count()).isZero();
    }

    @Test
    @DisplayName("PENDING 이벤트가 없으면 대기 작업을 만들지 않는다")
    void createDispatchJobs_noPendingEvent_createsNothing() {
        NotificationEvent event = notificationEventRepository.saveAndFlush(event(1L));
        event.markProcessed();
        notificationEventRepository.saveAndFlush(event);

        int created = notificationDispatchService.createDispatchJobs();

        assertThat(created).isZero();
        assertThat(notificationDispatchRepository.count()).isZero();
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
        return NotificationEvent.builder()
                .eventType(NotificationEventType.IMMINENT)
                .exceptionId(exceptionId)
                .shopId(SHOP_ID)
                .slotStartsAt(SLOT_START)
                .slotEndsAt(SLOT_START.plusHours(1))
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
