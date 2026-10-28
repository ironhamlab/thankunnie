package com.ucamp.app.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.notification.NotificationFixture;
import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventType;
import com.ucamp.app.domain.notification.repository.NotificationEventRepository;
import com.ucamp.app.domain.notification.repository.VacancySlotRepository;
import com.ucamp.app.domain.notification.service.NotificationEventService;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

// 스케줄 메서드가 각자 커밋하므로 테스트 데이터도 커밋하고 매번 지운다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationEventService.class, VacancySlotRepository.class, AvailabilityService.class,
        ImminentEventScheduler.class, ImminentEventSchedulerTest.FixedClock.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ImminentEventSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 9, 0);
    private static final long SHOP_ID = 1L;

    @Autowired
    private ImminentEventScheduler scheduler;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.shop(jdbcTemplate, SHOP_ID, 10L, "SEOUL_GANGNAM", "ACTIVE");
    }

    @AfterEach
    void tearDown() {
        List.of("NOTIFICATION_EVENT", "SHOP_SCHEDULE_EXCEPTION", "SHOP", "CATEGORY")
                .forEach(table -> jdbcTemplate.update("DELETE FROM " + table));
    }

    @Test
    @DisplayName("시작 3시간 이내 빈자리는 초임박 감지가 이벤트를 만들고, 임박 감지는 만들지 않는다")
    void detectLastMinute_withinThreeHours_createsOnlyLastMinute() {
        openSlot(1L, NOW.plusHours(2), NOW.plusHours(4));

        scheduler.detectImminent();
        scheduler.detectLastMinute();

        assertThat(notificationEventRepository.findAll())
                .extracting(NotificationEvent::getEventType)
                .containsExactly(NotificationEventType.LAST_MINUTE);
    }

    @Test
    @DisplayName("초임박 감지를 다시 해도 같은 빈자리 이벤트는 한 건이다")
    void detectLastMinute_runTwice_createsOnce() {
        openSlot(1L, NOW.plusHours(2), NOW.plusHours(4));

        scheduler.detectLastMinute();
        scheduler.detectLastMinute();

        assertThat(notificationEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("3시간보다 많이 남은 빈자리는 임박 감지만 이벤트를 만든다")
    void detectImminent_betweenThreeAndEightHours_createsOnlyImminent() {
        openSlot(1L, NOW.plusHours(5), NOW.plusHours(7));

        scheduler.detectLastMinute();
        scheduler.detectImminent();

        assertThat(notificationEventRepository.findAll())
                .extracting(NotificationEvent::getEventType)
                .containsExactly(NotificationEventType.IMMINENT);
    }

    @Test
    @DisplayName("임박 감지와 초임박 감지는 각자 자기 구간 이벤트만 저장한다")
    void detectImminentAndLastMinute_eachSavesItsOwnEvent() {
        openSlot(1L, NOW.plusHours(5), NOW.plusHours(7));
        openSlot(2L, NOW.plusHours(2), NOW.plusHours(4));

        scheduler.detectImminent();
        scheduler.detectLastMinute();

        assertThat(notificationEventRepository.findAll())
                .extracting(NotificationEvent::getEventType)
                .containsExactlyInAnyOrder(NotificationEventType.IMMINENT, NotificationEventType.LAST_MINUTE);
    }

    private void openSlot(long exceptionId, LocalDateTime startsAt, LocalDateTime endsAt) {
        NotificationFixture.exception(jdbcTemplate, exceptionId, SHOP_ID, null, "OPEN", startsAt, endsAt);
    }

    /** 스케줄러와 저장 시각을 테스트의 NOW 로 고정한다. */
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
