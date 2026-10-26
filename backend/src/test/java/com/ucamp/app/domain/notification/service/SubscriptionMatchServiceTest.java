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
import com.ucamp.app.domain.notification.repository.ShopCategoryRegionRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SubscriptionMatchService.class, ShopCategoryRegionRepository.class,
        SubscriptionMatchServiceTest.FixedClock.class})
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
        repository.save(categoryRegion(2L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

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
    @DisplayName("매장 카테고리·지역코드와 같고 빈자리 시작이 희망 시간 안인 구독만 고른다")
    void findCategoryRegionMatches_sameCategoryRegionAndTime_returnsThoseSubscriptions() {
        NotificationSubscription target = repository.save(
                categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, 10L, "SEOUL_MAPO", NOW, NOW.plusHours(5)));
        repository.save(store(2L, 1L, NOW, NOW.plusHours(5)));

        List<NotificationSubscription> result = subscriptionMatchService.findCategoryRegionMatches(1L, SLOT_START);

        assertThat(result).extracting(NotificationSubscription::getId).containsExactly(target.getId());
    }

    @Test
    @DisplayName("카테고리가 다른 매장의 빈자리는 고르지 않는다")
    void findCategoryRegionMatches_differentCategory_isExcluded() {
        NotificationFixture.category(jdbcTemplate, 20L, "HAIR");
        NotificationFixture.shop(jdbcTemplate, 3L, 20L, "SEOUL_GANGNAM", "ACTIVE");
        repository.save(categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findCategoryRegionMatches(3L, SLOT_START)).isEmpty();
    }

    @Test
    @DisplayName("카테고리 구독도 희망 시간 경계는 포함하고 해제·만료는 뺀다")
    void findCategoryRegionMatches_timeBoundaryAndInactiveOrExpired() {
        NotificationSubscription startsAtSlot = repository.save(
                categoryRegion(1L, 10L, "SEOUL_GANGNAM", SLOT_START, SLOT_START.plusHours(1)));
        NotificationSubscription endsAtSlot = repository.save(
                categoryRegion(2L, 10L, "SEOUL_GANGNAM", NOW, SLOT_START));
        repository.save(categoryRegion(1L, 10L, "SEOUL_GANGNAM", SLOT_START.plusHours(1), SLOT_START.plusHours(2)));

        NotificationSubscription inactive = categoryRegion(2L, 10L, "SEOUL_MAPO", NOW, NOW.plusHours(5));
        inactive.deactivate();
        repository.save(inactive);
        repository.save(categoryRegion(1L, 10L, "SEOUL_MAPO", NOW.minusHours(5), NOW.minusHours(1)));

        assertThat(subscriptionMatchService.findCategoryRegionMatches(1L, SLOT_START))
                .extracting(NotificationSubscription::getId)
                .containsExactlyInAnyOrder(startsAtSlot.getId(), endsAtSlot.getId());
        assertThat(subscriptionMatchService.findCategoryRegionMatches(2L, NOW.minusHours(2))).isEmpty();
    }

    @Test
    @DisplayName("매장이 없거나 매장 ID·빈자리 시작 시각이 없으면 카테고리 구독은 빈 목록이다")
    void findCategoryRegionMatches_missingShopOrInput_returnsEmpty() {
        repository.save(categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findCategoryRegionMatches(999L, SLOT_START)).isEmpty();
        assertThat(subscriptionMatchService.findCategoryRegionMatches(null, SLOT_START)).isEmpty();
        assertThat(subscriptionMatchService.findCategoryRegionMatches(1L, null)).isEmpty();
    }

    @Test
    @DisplayName("업장 구독만 맞아도 그 회원을 발송 대상으로 고른다")
    void findRecipientUserIds_storeOnly_returnsThatUser() {
        repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, 10L, "SEOUL_MAPO", NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findRecipientUserIds(1L, SLOT_START)).containsExactly(1L);
    }

    @Test
    @DisplayName("카테고리·지역 구독만 맞아도 그 회원을 발송 대상으로 고른다")
    void findRecipientUserIds_categoryOnly_returnsThatUser() {
        repository.save(store(1L, 2L, NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findRecipientUserIds(1L, SLOT_START)).containsExactly(2L);
    }

    @Test
    @DisplayName("한 회원이 업장 구독과 카테고리 구독에 모두 맞아도 한 번만 고른다")
    void findRecipientUserIds_bothConditions_returnsUserOnce() {
        repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));
        repository.save(store(1L, 1L, NOW, NOW.plusHours(4)));
        repository.save(categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findRecipientUserIds(1L, SLOT_START)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("둘 다 안 맞거나 해제·만료된 구독의 회원은 발송 대상이 아니다")
    void findRecipientUserIds_noMatchOrInactiveOrExpired_isExcluded() {
        repository.save(store(1L, 2L, NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, 10L, "SEOUL_MAPO", NOW, NOW.plusHours(5)));

        NotificationSubscription inactive = store(1L, 1L, NOW, NOW.plusHours(5));
        inactive.deactivate();
        repository.save(inactive);
        repository.save(categoryRegion(2L, 10L, "SEOUL_GANGNAM", NOW.minusHours(5), NOW.minusHours(1)));

        assertThat(subscriptionMatchService.findRecipientUserIds(1L, SLOT_START)).isEmpty();
    }

    @Test
    @DisplayName("매장 ID나 빈자리 시작 시각이 없으면 발송 대상도 빈 목록이다")
    void findRecipientUserIds_missingInput_returnsEmpty() {
        repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findRecipientUserIds(null, SLOT_START)).isEmpty();
        assertThat(subscriptionMatchService.findRecipientUserIds(1L, null)).isEmpty();
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

    private static NotificationSubscription categoryRegion(Long userId, Long categoryId, String regionCode,
            LocalDateTime startTime, LocalDateTime endTime) {
        return NotificationSubscription.builder()
                .userId(userId)
                .subscriptionType(SubscriptionType.CATEGORY_REGION)
                .categoryId(categoryId)
                .regionCode(regionCode)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }
}
