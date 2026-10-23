package com.ucamp.app.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.config.JpaAuditingConfig;
import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionStatus;
import com.ucamp.app.domain.notification.entity.SubscriptionType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class NotificationSubscriptionRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);
    private static final LocalDateTime SLOT_START = NOW.plusHours(3);

    @Autowired
    private NotificationSubscriptionRepository repository;

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
    @DisplayName("내 구독 목록은 본인의 ACTIVE 구독만 최신 등록 순으로 반환한다")
    void findAllByUserIdAndStatus_mixedSubscriptions_returnsOwnActiveOnly() {
        NotificationSubscription first = repository.save(store(1L, 1L, NOW, NOW.plusDays(1)));
        NotificationSubscription second = repository.save(categoryRegion(1L, "NAIL", "SEOUL_GANGNAM",
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
    @DisplayName("업장 알림 대상은 같은 매장의 희망 시간에 빈자리 시작 시각이 포함된 ACTIVE 구독이다")
    void findNotifiableByShop_activeInRange_returnsSubscription() {
        NotificationSubscription target = repository.save(store(1L, 1L, NOW, NOW.plusHours(5)));
        repository.save(store(2L, 2L, NOW, NOW.plusHours(5)));
        repository.save(store(3L, 1L, NOW.plusHours(4), NOW.plusHours(6)));

        List<NotificationSubscription> result = repository.findNotifiableByShop(1L, SLOT_START, NOW);

        assertThat(result).extracting(NotificationSubscription::getId).containsExactly(target.getId());
    }

    @Test
    @DisplayName("해제한 구독은 알림 대상에서 제외된다")
    void findNotifiableByShop_inactive_excluded() {
        NotificationSubscription subscription = store(1L, 1L, NOW, NOW.plusHours(5));
        subscription.deactivate();
        repository.save(subscription);

        assertThat(repository.findNotifiableByShop(1L, SLOT_START, NOW)).isEmpty();
    }

    @Test
    @DisplayName("희망 종료 시각이 지난 만료 구독은 알림 대상에서 제외된다")
    void findNotifiableByShop_expired_excluded() {
        repository.save(store(1L, 1L, NOW.minusHours(5), NOW.minusHours(1)));

        assertThat(repository.findNotifiableByShop(1L, NOW.minusHours(2), NOW)).isEmpty();
    }

    @Test
    @DisplayName("카테고리·지역 알림 대상은 카테고리와 지역이 모두 같은 ACTIVE 구독이다")
    void findNotifiableByCategoryRegion_sameCategoryAndRegion_returnsSubscription() {
        NotificationSubscription target = repository.save(categoryRegion(1L, "NAIL", "SEOUL_GANGNAM",
                NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(2L, "NAIL", "SEOUL_MAPO", NOW, NOW.plusHours(5)));
        repository.save(categoryRegion(3L, "HAIR", "SEOUL_GANGNAM", NOW, NOW.plusHours(5)));
        repository.save(store(4L, 1L, NOW, NOW.plusHours(5)));

        List<NotificationSubscription> result =
                repository.findNotifiableByCategoryRegion("NAIL", "SEOUL_GANGNAM", SLOT_START, NOW);

        assertThat(result).extracting(NotificationSubscription::getId).containsExactly(target.getId());
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

    private static NotificationSubscription categoryRegion(Long userId, String categoryName, String regionName,
            LocalDateTime startTime, LocalDateTime endTime) {
        return NotificationSubscription.builder()
                .userId(userId)
                .subscriptionType(SubscriptionType.CATEGORY_REGION)
                .shopId(1L)
                .categoryName(categoryName)
                .regionName(regionName)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }
}
