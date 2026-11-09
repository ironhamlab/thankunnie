package com.ucamp.app.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.notification.NotificationFixture;
import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionStatus;
import com.ucamp.app.domain.notification.entity.SubscriptionType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig.class)
@ActiveProfiles("test")
class NotificationSubscriptionRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);
    private static final LocalDateTime SLOT_START = NOW.plusHours(3);

    @Autowired
    private NotificationSubscriptionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 알림은 회원·매장·카테고리를 FK 로 참조한다
    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.category(jdbcTemplate, 20L, "HAIR");
        NotificationFixture.shop(jdbcTemplate, 1L, 10L, "SEOUL_GANGNAM", "ACTIVE");
        NotificationFixture.shop(jdbcTemplate, 2L, 10L, "SEOUL_MAPO", "ACTIVE");
        for (long userId = 1L; userId <= 4L; userId++) {
            NotificationFixture.user(jdbcTemplate, userId);
        }
    }

    @Test
    @DisplayName("저장하면 시퀀스 ID 와 생성·수정 시각이 채워지고 ACTIVE 로 시작한다")
    void save_newSubscription_assignsIdAndAuditFields() {
        NotificationSubscription saved = repository.saveAndFlush(store(1L, 1L, NOW, NOW.plusDays(1)));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("내 알림 목록은 본인의 ACTIVE 알림만 최신 등록 순으로 반환한다")
    void findAllByUserIdAndStatus_mixedSubscriptions_returnsOwnActiveOnly() {
        NotificationSubscription first = repository.save(store(1L, 1L, NOW, NOW.plusDays(1)));
        NotificationSubscription second = repository.save(categoryRegion(1L, 10L, "SEOUL_GANGNAM",
                NOW, NOW.plusDays(1)));
        NotificationSubscription inactive = store(1L, 2L, NOW, NOW.plusDays(1));
        inactive.deactivate();
        repository.save(inactive);
        repository.save(store(2L, 1L, NOW, NOW.plusDays(1)));

        List<NotificationSubscription> result =
                repository.findAllByUserIdAndStatusOrderByIdDesc(1L, SubscriptionStatus.ACTIVE);

        assertThat(result).extracting(NotificationSubscription::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("업장 알림 대상은 같은 매장의 희망 시간에 빈자리 시작 시각이 포함된 ACTIVE 알림이다")
    void findNotifiableByShop_activeInRange_returnsSubscription() {
        NotificationSubscription target = repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));
        repository.save(store(2L, 2L, NOW, NOW.plusHours(5)));
        repository.save(store(3L, 1L, NOW.plusHours(4), NOW.plusHours(6)));

        List<NotificationSubscription> result = repository.findNotifiableByShop(1L, SLOT_START, NOW);

        assertThat(result).extracting(NotificationSubscription::getId).containsExactly(target.getId());
    }

    @Test
    @DisplayName("해제한 알림은 알림 대상에서 제외된다")
    void findNotifiableByShop_inactive_excluded() {
        NotificationSubscription subscription = store(1L, 1L, NOW, NOW.plusHours(5));
        subscription.deactivate();
        repository.save(subscription);

        assertThat(repository.findNotifiableByShop(1L, SLOT_START, NOW)).isEmpty();
    }

    @Test
    @DisplayName("희망 종료 시각이 지난 만료 알림은 알림 대상에서 제외된다")
    void findNotifiableByShop_expired_excluded() {
        repository.save(store(1L, 1L, NOW.minusHours(5), NOW.minusHours(1)));

        assertThat(repository.findNotifiableByShop(1L, NOW.minusHours(2), NOW)).isEmpty();
    }

    @Test
    @DisplayName("카테고리·지역 알림 대상은 카테고리와 지역이 모두 같은 ACTIVE 알림이다")
    void findNotifiableByCategoryRegion_sameCategoryAndRegion_returnsSubscription() {
        NotificationSubscription target = repository.save(categoryRegion(1L, 10L, "SEOUL_GANGNAM",
                NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, 10L, "SEOUL_MAPO", NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(3L, 20L, "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));
        repository.save(store(4L, 1L, NOW, NOW.plusHours(5)));

        List<NotificationSubscription> result =
                repository.findNotifiableByCategoryRegion(10L, "SEOUL_GANGNAM", SLOT_START, NOW);

        assertThat(result).extracting(NotificationSubscription::getId).containsExactly(target.getId());
    }

    private static NotificationSubscription store(Long userId, Long shopId,
            LocalDateTime startTime, LocalDateTime endTime) {
        return NotificationSubscription.builder()
                .userId(userId)
                .subscriptionType(SubscriptionType.STORE)
                .treatmentId(100L)
                .shopId(shopId)
                .startTime(startTime)
                .endTime(endTime)
                .build();
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
