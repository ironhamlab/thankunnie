package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
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
import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionType;
import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SubscriptionMatchService.class, SubscriptionMatchServiceTest.FixedClock.class})
@ActiveProfiles("test")
class SubscriptionMatchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);
    private static final LocalDateTime SLOT_START = NOW.plusHours(3);

    @Autowired
    private SubscriptionMatchService subscriptionMatchService;

    @Autowired
    private NotificationSubscriptionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.shop(jdbcTemplate, 1L, 10L, "SEOUL_GANGNAM", "ACTIVE");
        NotificationFixture.shop(jdbcTemplate, 2L, 10L, "SEOUL_MAPO", "ACTIVE");
        NotificationFixture.user(jdbcTemplate, 1L);
        NotificationFixture.user(jdbcTemplate, 2L);
    }

    @Test
    @DisplayName("같은 매장이고 빈자리 시작이 희망 시간 안인 업장 구독만 고른다")
    void findStoreMatches_sameShopAndTime_returnsThoseSubscriptions() {
        NotificationSubscription target = repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));
        repository.save(store(2L, 2L, NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, NOW, NOW.plusHours(5)));

        List<NotificationSubscription> result = subscriptionMatchService.findStoreMatches(1L, SLOT_START);

        assertThat(result).extracting(NotificationSubscription::getId).containsExactly(target.getId());
    }

    @Test
    @DisplayName("희망 시작과 희망 종료와 같은 빈자리 시작 시각도 포함한다")
    void findStoreMatches_slotOnHopeBoundary_isIncluded() {
        NotificationSubscription target = repository.save(store(1L, 1L, SLOT_START, SLOT_START.plusHours(1)));
        repository.save(store(2L, 1L, SLOT_START.plusHours(1), SLOT_START.plusHours(2)));

        // 희망 종료가 빈자리 시작과 같은 구독. 종료 시각도 희망 시간에 포함된다.
        NotificationSubscription endsAtSlot = repository.save(store(2L, 1L, NOW, SLOT_START));

        List<NotificationSubscription> result = subscriptionMatchService.findStoreMatches(1L, SLOT_START);

        assertThat(result).extracting(NotificationSubscription::getId)
                .containsExactlyInAnyOrder(target.getId(), endsAtSlot.getId());
    }

    @Test
    @DisplayName("해제한 구독과 희망 종료가 지난 구독은 빠진다")
    void findStoreMatches_inactiveOrExpired_isExcluded() {
        NotificationSubscription inactive = store(1L, 1L, NOW, NOW.plusHours(5));
        inactive.deactivate();
        repository.save(inactive);
        repository.save(store(2L, 1L, NOW.minusHours(5), NOW.minusHours(1)));

        assertThat(subscriptionMatchService.findStoreMatches(1L, NOW.minusHours(2))).isEmpty();
    }

    @Test
    @DisplayName("매장 ID나 빈자리 시작 시각이 없으면 빈 목록이다")
    void findStoreMatches_missingInput_returnsEmpty() {
        repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findStoreMatches(null, SLOT_START)).isEmpty();
        assertThat(subscriptionMatchService.findStoreMatches(1L, null)).isEmpty();
    }

    private static NotificationSubscription store(Long userId, Long shopId,
            LocalDateTime startTime, LocalDateTime endTime) {
        return NotificationSubscription.builder()
                .userId(userId)
                .subscriptionType(SubscriptionType.STORE)
                .shopId(shopId)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }

    /** 서비스와 저장 시각을 테스트의 NOW 로 고정한다. */
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

    private static NotificationSubscription categoryRegion(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        return NotificationSubscription.builder()
                .userId(userId)
                .subscriptionType(SubscriptionType.CATEGORY_REGION)
                .categoryId(10L)
                .regionCode("SEOUL_GANGNAM")
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }
}
