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

    // JPQL 문자열 안에서는 import가 되지 않아 enum은 패키지 이름까지 적는다.
    String STORE = "com.ucamp.app.domain.notification.entity.SubscriptionType.STORE";
    String CATEGORY_REGION = "com.ucamp.app.domain.notification.entity.SubscriptionType.CATEGORY_REGION";
    String ACTIVE = "com.ucamp.app.domain.notification.entity.SubscriptionStatus.ACTIVE";

    // 빈자리 시작이 희망 시작 이상, 희망 종료 이하이고, 희망 종료가 현재보다 뒤다.
    String IN_HOPE_TIME = """
              and s.startTime <= :slotStartTime
              and s.endTime >= :slotStartTime
              and s.endTime > :now
            """;

    // 이 매장의 업장 구독. 해제한 구독은 빠진다.
    String FIND_NOTIFIABLE_BY_SHOP = """
            select s from NotificationSubscription s
            where s.subscriptionType = \
            """
            + STORE
            + """
              and s.status = \
            """
            + ACTIVE
            + """
              and s.shopId = :shopId
            """
            + IN_HOPE_TIME;

    // 카테고리와 지역이 같은 구독. 해제한 구독은 빠진다.
    String FIND_NOTIFIABLE_BY_CATEGORY_REGION = """
            select s from NotificationSubscription s
            where s.subscriptionType = \
            """
            + CATEGORY_REGION
            + """
              and s.status = \
            """
            + ACTIVE
            + """
              and s.categoryId = :categoryId
              and s.regionCode = :regionCode
            """
            + IN_HOPE_TIME;

    /**
     * 회원의 구독을 상태별로 조회한다. ID는 시퀀스라 큰 값이 최근 등록이다.
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
     * 이 매장 업장 구독 중, 빈자리 시작 시각을 알려줄 대상을 찾는다.
     * 해제한 구독과, 희망 종료 시각이 이미 지난 구독은 제외한다.
     * 빈자리 시작 시각이 희망 시작 이상이고 희망 종료 이하이면 포함한다.
     *
     * @param shopId        매장 ID
     * @param slotStartTime 빈자리 시작 시각
     * @param now           현재 시각
     * @return 알림 대상 구독 목록
     */
    @Query(FIND_NOTIFIABLE_BY_SHOP)
    List<NotificationSubscription> findNotifiableByShop(@Param("shopId") Long shopId,
            @Param("slotStartTime") LocalDateTime slotStartTime, @Param("now") LocalDateTime now);

    /**
     * 카테고리와 지역이 같은 구독 중, 빈자리 시작 시각을 알려줄 대상을 찾는다.
     * 해제한 구독과, 희망 종료 시각이 이미 지난 구독은 제외한다.
     * 빈자리 시작 시각이 희망 시작 이상이고 희망 종료 이하이면 포함한다.
     *
     * @param categoryId    카테고리 ID
     * @param regionCode    지역코드
     * @param slotStartTime 빈자리 시작 시각
     * @param now           현재 시각
     * @return 알림 대상 구독 목록
     */
    @Query(FIND_NOTIFIABLE_BY_CATEGORY_REGION)
    List<NotificationSubscription> findNotifiableByCategoryRegion(@Param("categoryId") Long categoryId,
            @Param("regionCode") String regionCode, @Param("slotStartTime") LocalDateTime slotStartTime,
            @Param("now") LocalDateTime now);
}
