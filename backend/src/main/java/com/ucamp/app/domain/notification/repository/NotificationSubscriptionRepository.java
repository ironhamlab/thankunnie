package com.ucamp.app.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionStatus;

/**
 * 알림 구독 저장소.
 */
public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    List<NotificationSubscription> findAllByUserIdAndStatusOrderByIdDesc(Long userId, SubscriptionStatus status);

    Optional<NotificationSubscription> findByIdAndUserId(Long id, Long userId);

    /**
     * 매장의 빈자리 시작 시각이 희망 시간에 포함되는 알림 대상 구독. 해제(INACTIVE)·만료 구독은 제외한다.
     */
    @Query("""
            select s from NotificationSubscription s
            where s.subscriptionType = com.ucamp.app.domain.notification.entity.SubscriptionType.STORE
              and s.status = com.ucamp.app.domain.notification.entity.SubscriptionStatus.ACTIVE
              and s.shopId = :shopId
              and s.startTime <= :slotStartTime
              and s.endTime >= :slotStartTime
              and s.endTime > :now
            """)
    List<NotificationSubscription> findNotifiableByShop(@Param("shopId") Long shopId,
            @Param("slotStartTime") LocalDateTime slotStartTime, @Param("now") LocalDateTime now);

    /**
     * 카테고리·지역이 같고 빈자리 시작 시각이 희망 시간에 포함되는 알림 대상 구독. 해제(INACTIVE)·만료 구독은 제외한다.
     */
    @Query("""
            select s from NotificationSubscription s
            where s.subscriptionType = com.ucamp.app.domain.notification.entity.SubscriptionType.CATEGORY_REGION
              and s.status = com.ucamp.app.domain.notification.entity.SubscriptionStatus.ACTIVE
              and s.categoryCode = :categoryCode
              and s.regionCode = :regionCode
              and s.startTime <= :slotStartTime
              and s.endTime >= :slotStartTime
              and s.endTime > :now
            """)
    List<NotificationSubscription> findNotifiableByCategoryRegion(@Param("categoryCode") String categoryCode,
            @Param("regionCode") String regionCode, @Param("slotStartTime") LocalDateTime slotStartTime,
            @Param("now") LocalDateTime now);
}
