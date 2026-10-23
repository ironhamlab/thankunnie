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
 * 알림 구독 저장소. 본인 구독 조회와 알림 대상 조회를 제공한다.
 */
public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    /**
     * 회원의 구독을 상태별로, 최근 등록 순으로 조회한다.
     *
     * @param userId 회원 ID
     * @param status 구독 상태
     * @return 구독 목록
     */
    List<NotificationSubscription> findAllByUserIdAndStatusOrderByIdDesc(Long userId, SubscriptionStatus status);

    /**
     * 구독 ID와 회원 ID가 모두 일치하는 구독을 조회한다.
     *
     * @param id     구독 ID
     * @param userId 회원 ID
     * @return 일치하는 구독. 없으면 empty
     */
    Optional<NotificationSubscription> findByIdAndUserId(Long id, Long userId);

    /**
     * 매장의 빈자리 시작 시각이 희망 시간에 포함되는 알림 대상 구독을 조회한다. 해제·만료 구독은 제외한다.
     *
     * @param shopId        매장 ID
     * @param slotStartTime 빈자리 시작 시각
     * @param now           현재 시각
     * @return 알림 대상 구독 목록
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
     * 카테고리·지역이 같고 빈자리 시작 시각이 희망 시간에 포함되는 알림 대상 구독을 조회한다. 해제·만료 구독은 제외한다.
     *
     * @param categoryName  카테고리명
     * @param regionCode    지역코드
     * @param slotStartTime 빈자리 시작 시각
     * @param now           현재 시각
     * @return 알림 대상 구독 목록
     */
    @Query("""
            select s from NotificationSubscription s
            where s.subscriptionType = com.ucamp.app.domain.notification.entity.SubscriptionType.CATEGORY_REGION
              and s.status = com.ucamp.app.domain.notification.entity.SubscriptionStatus.ACTIVE
              and s.categoryName = :categoryName
              and s.regionCode = :regionCode
              and s.startTime <= :slotStartTime
              and s.endTime >= :slotStartTime
              and s.endTime > :now
            """)
    List<NotificationSubscription> findNotifiableByCategoryRegion(@Param("categoryName") String categoryName,
            @Param("regionCode") String regionCode, @Param("slotStartTime") LocalDateTime slotStartTime,
            @Param("now") LocalDateTime now);
}
