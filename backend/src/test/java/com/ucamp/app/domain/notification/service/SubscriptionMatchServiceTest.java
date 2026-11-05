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
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SubscriptionMatchService.class, StoreSubscriptionTreatmentService.class,
        ShopCategoryRegionRepository.class, AvailabilityService.class,
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
        // 2026-10-22 는 목요일. 15:00 시작 60분 시술이 영업시간 안에 들어가게 연다.
        NotificationFixture.treatment(jdbcTemplate, 100L, 1L, "NORMAL");
        NotificationFixture.businessHour(jdbcTemplate, 50L, 1L, "THU", "10:00", "20:00");
    }

    @Test
    @DisplayName("같은 매장이고 빈자리 시작이 희망 시간 안인 업장 알림만 고른다")
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

        // 희망 종료가 빈자리 시작과 같은 알림. 종료 시각도 희망 시간에 포함된다.
        NotificationSubscription endsAtSlot = repository.save(store(2L, 1L, NOW, SLOT_START));

        List<NotificationSubscription> result = subscriptionMatchService.findStoreMatches(1L, SLOT_START);

        assertThat(result).extracting(NotificationSubscription::getId)
                .containsExactlyInAnyOrder(target.getId(), endsAtSlot.getId());
    }

    @Test
    @DisplayName("고른 시술이 없거나 판매 중이 아니면 업장 알림은 빠진다")
    void findStoreMatches_missingOrInactiveTreatment_isExcluded() {
        repository.save(store(1L, 1L, 999L, NOW, NOW.plusHours(5)));
        jdbcTemplate.update("UPDATE TREATMENT SET STATUS = 'INACTIVE' WHERE TREATMENT_ID = 100");
        repository.save(store(2L, 1L, 100L, NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findStoreMatches(1L, SLOT_START)).isEmpty();
    }

    @Test
    @DisplayName("고른 시술을 그 시각에 예약할 수 없으면 업장 알림은 빠진다")
    void findStoreMatches_treatmentNotBookable_isExcluded() {
        jdbcTemplate.update("UPDATE TREATMENT SET DURATION_MINUTES = 600 WHERE TREATMENT_ID = 100");
        repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findStoreMatches(1L, SLOT_START)).isEmpty();
    }

    @Test
    @DisplayName("같은 회원의 시술 알림이 여러 개 그 시각에 되면 알림은 모두 남고 회원은 한 번만 보낸다")
    void findStoreMatches_severalBookableTreatments_keepsSubscriptionsAndOneUser() {
        NotificationFixture.treatment(jdbcTemplate, 101L, 1L, "NORMAL");
        NotificationSubscription smaller = repository.save(store(1L, 1L, 100L, NOW, NOW.plusHours(5)));
        NotificationSubscription larger = repository.save(store(1L, 1L, 101L, NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findStoreMatches(1L, SLOT_START))
                .extracting(NotificationSubscription::getId)
                .containsExactlyInAnyOrder(smaller.getId(), larger.getId());
        assertThat(subscriptionMatchService.findRecipientUserIds(1L, SLOT_START)).containsExactly(1L);
    }

    @Test
    @DisplayName("해제한 알림과 희망 종료가 지난 알림은 빠진다")
    void findStoreMatches_inactiveOrExpired_isExcluded() {
        NotificationSubscription inactive = store(1L, 1L, NOW, NOW.plusHours(5));
        inactive.deactivate();
        repository.save(inactive);
        repository.save(store(2L, 1L, NOW.minusHours(5), NOW.minusHours(1)));

        assertThat(subscriptionMatchService.findStoreMatches(1L, NOW.minusHours(2))).isEmpty();
    }

    @Test
    @DisplayName("매장 카테고리·지역코드와 같고 빈자리 시작이 희망 시간 안인 알림만 고른다")
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
    @DisplayName("카테고리 알림도 희망 시간 경계는 포함하고 해제·만료는 뺀다")
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
    @DisplayName("매장이 없거나 매장 ID·빈자리 시작 시각이 없으면 카테고리 알림은 빈 목록이다")
    void findCategoryRegionMatches_missingShopOrInput_returnsEmpty() {
        repository.save(categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findCategoryRegionMatches(999L, SLOT_START)).isEmpty();
        assertThat(subscriptionMatchService.findCategoryRegionMatches(null, SLOT_START)).isEmpty();
        assertThat(subscriptionMatchService.findCategoryRegionMatches(1L, null)).isEmpty();
    }

    @Test
    @DisplayName("업장 알림으로만 이 빈자리 알림을 받을 수 있어도 그 회원을 발송 대상으로 고른다")
    void findRecipientUserIds_storeOnly_returnsThatUser() {
        repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, 10L, "SEOUL_MAPO", NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findRecipientUserIds(1L, SLOT_START)).containsExactly(1L);
    }

    @Test
    @DisplayName("카테고리·지역 알림으로만 이 빈자리 알림을 받을 수 있어도 그 회원을 발송 대상으로 고른다")
    void findRecipientUserIds_categoryOnly_returnsThatUser() {
        repository.save(store(1L, 2L, NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findRecipientUserIds(1L, SLOT_START)).containsExactly(2L);
    }

    @Test
    @DisplayName("한 회원이 업장 알림과 카테고리 알림으로 모두 알림을 받을 수 있어도 한 번만 고른다")
    void findRecipientUserIds_bothConditions_returnsUserOnce() {
        repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));
        repository.save(store(1L, 1L, NOW, NOW.plusHours(4)));
        repository.save(categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

        assertThat(subscriptionMatchService.findRecipientUserIds(1L, SLOT_START)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("둘 다 안 맞거나 해제·만료된 알림의 회원은 발송 대상이 아니다")
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
    @DisplayName("매장 알림으로 이 빈자리 알림을 받을 회원은 카테고리 알림이 여러 개여도 카테고리 목록에 넣지 않는다")
    void findMatchGroups_storeUser_isRemovedFromCategorySubscriptions() {
        NotificationSubscription storeMatch = repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));
        repository.save(store(1L, 1L, NOW, NOW.plusHours(4)));
        repository.save(categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(4)));
        NotificationSubscription otherCategory = repository.save(
                categoryRegion(2L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

        SubscriptionMatchGroups groups = subscriptionMatchService.findMatchGroups(1L, SLOT_START);

        assertThat(groups.storeUserIds()).containsExactly(storeMatch.getUserId());
        assertThat(groups.categoryRegionOnlySubscriptions())
                .extracting(NotificationSubscription::getId)
                .containsExactly(otherCategory.getId());
    }

    @Test
    @DisplayName("카테고리 알림으로만 이 빈자리 알림을 받을 회원은 알림이 두 개면 두 개 다 남긴다")
    void findMatchGroups_categoryOnlyUser_keepsEverySubscription() {
        NotificationSubscription first = repository.save(
                categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));
        NotificationSubscription second = repository.save(
                categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(4)));
        repository.save(store(2L, 2L, NOW, NOW.plusHours(5)));

        SubscriptionMatchGroups groups = subscriptionMatchService.findMatchGroups(1L, SLOT_START);

        assertThat(groups.storeUserIds()).isEmpty();
        assertThat(groups.categoryRegionOnlySubscriptions())
                .extracting(NotificationSubscription::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    @DisplayName("해제한 매장 알림은 같은 회원의 카테고리 알림을 빼지 않는다")
    void findMatchGroups_inactiveStore_doesNotRemoveCategory() {
        NotificationSubscription inactiveStore = store(1L, 1L, NOW, NOW.plusHours(5));
        inactiveStore.deactivate();
        repository.save(inactiveStore);
        NotificationSubscription category = repository.save(
                categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

        SubscriptionMatchGroups groups = subscriptionMatchService.findMatchGroups(1L, SLOT_START);

        assertThat(groups.storeUserIds()).isEmpty();
        assertThat(groups.categoryRegionOnlySubscriptions())
                .extracting(NotificationSubscription::getId)
                .containsExactly(category.getId());
    }

    @Test
    @DisplayName("이 빈자리의 매장 알림이 아니면 카테고리 알림은 빼지 않는다")
    void findMatchGroups_storeSubscriptionForAnotherShop_doesNotRemoveCategory() {
        repository.save(store(1L, 2L, NOW, NOW.plusHours(5)));
        NotificationSubscription category = repository.save(
                categoryRegion(1L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

        SubscriptionMatchGroups groups = subscriptionMatchService.findMatchGroups(1L, SLOT_START);

        assertThat(groups.storeUserIds()).isEmpty();
        assertThat(groups.categoryRegionOnlySubscriptions())
                .extracting(NotificationSubscription::getId)
                .containsExactly(category.getId());
    }

    @Test
    @DisplayName("매장 ID나 빈자리 시작 시각이 없으면 나눈 결과도 양쪽이 빈 목록이다")
    void findMatchGroups_missingInput_returnsEmptyGroups() {
        repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, 10L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));

        SubscriptionMatchGroups missingShop = subscriptionMatchService.findMatchGroups(null, SLOT_START);
        SubscriptionMatchGroups missingSlot = subscriptionMatchService.findMatchGroups(1L, null);

        assertThat(missingShop.storeUserIds()).isEmpty();
        assertThat(missingShop.categoryRegionOnlySubscriptions()).isEmpty();
        assertThat(missingSlot.storeUserIds()).isEmpty();
        assertThat(missingSlot.categoryRegionOnlySubscriptions()).isEmpty();
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
        return store(userId, shopId, 100L, startTime, endTime);
    }

    private static NotificationSubscription store(Long userId, Long shopId, Long treatmentId,
            LocalDateTime startTime, LocalDateTime endTime) {
        return NotificationSubscription.builder()
                .userId(userId)
                .subscriptionType(SubscriptionType.STORE)
                .treatmentId(treatmentId)
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
