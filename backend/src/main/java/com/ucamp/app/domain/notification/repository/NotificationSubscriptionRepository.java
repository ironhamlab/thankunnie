package com.ucamp.app.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionStatus;

/**
 * 알림 저장소. 본인 알림 조회와 알림 대상 조회를 제공한다.
 */
public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    // JPQL 문자열 안에서는 import가 되지 않아 enum은 패키지 이름까지 적는다.
    String STORE = "com.ucamp.app.domain.notification.entity.SubscriptionType.STORE";
    String CATEGORY_REGION = "com.ucamp.app.domain.notification.entity.SubscriptionType.CATEGORY_REGION";
    String ACTIVE = "com.ucamp.app.domain.notification.entity.SubscriptionStatus.ACTIVE";

    // 빈자리 시작이 희망 시작 이상, 희망 종료 이하이고, 희망 종료가 현재보다 뒤다.
    // 희망 시간이 비어 있는 업장 찜은 비교가 성립하지 않아 알림 대상에서 빠진다.
    String IN_HOPE_TIME = """
              and s.startTime <= :slotStartTime
              and s.endTime >= :slotStartTime
              and s.endTime > :now
            """;

    // 이 매장의 업장 알림. 해제한 알림은 빠진다.
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

    // 카테고리와 지역이 같은 알림. 해제한 알림은 빠진다.
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
     * 회원의 알림을 상태별로 조회한다. ID는 시퀀스라 큰 값이 최근 등록이다.
     *
     * @param userId 회원 ID
     * @param status 알림 상태
     * @return 알림 목록
     */
    List<NotificationSubscription> findAllByUserIdAndStatusOrderByIdDesc(Long userId, SubscriptionStatus status);

    /**
     * 알림 ID와 회원 ID가 모두 일치하는 알림을 조회한다.
     *
     * @param id     알림 ID
     * @param userId 회원 ID
     * @return 일치하는 알림. 없으면 empty
     */
    Optional<NotificationSubscription> findByIdAndUserId(Long id, Long userId);

    /**
     * 업장 알림 등록 시 중복 여부. ACTIVE 이고 회원·매장·시술·희망 시작·종료가 모두 같으면 true.
     */
    boolean existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTime(
            Long userId, SubscriptionStatus status, Long shopId, Long treatmentId,
            LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 업장 알림 수정 시 중복 여부. 자기 알림(id)은 제외하고 본다.
     */
    boolean existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTimeAndIdNot(
            Long userId, SubscriptionStatus status, Long shopId, Long treatmentId,
            LocalDateTime startTime, LocalDateTime endTime, Long id);

    /**
     * 시간이 비어 있는 같은 매장·같은 시술 찜이 이미 있는지. null 끼리의 같음은 = 비교로 잡히지 않는다.
     */
    boolean existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeIsNullAndEndTimeIsNull(
            Long userId, SubscriptionStatus status, Long shopId, Long treatmentId);

    /**
     * 시간이 비어 있는 같은 매장·같은 시술 찜이 있는지. 수정 시 자기 알림은 뺀다.
     */
    boolean existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeIsNullAndEndTimeIsNullAndIdNot(
            Long userId, SubscriptionStatus status, Long shopId, Long treatmentId, Long id);

    /**
     * 카테고리·지역 알림 등록 시 중복 여부. ACTIVE 이고 회원·카테고리·지역·희망 시작·종료가 모두 같으면 true.
     */
    boolean existsByUserIdAndStatusAndCategoryIdAndRegionCodeAndStartTimeAndEndTime(
            Long userId, SubscriptionStatus status, Long categoryId, String regionCode,
            LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 카테고리·지역 알림 수정 시 중복 여부. 자기 알림(id)은 제외하고 본다.
     */
    boolean existsByUserIdAndStatusAndCategoryIdAndRegionCodeAndStartTimeAndEndTimeAndIdNot(
            Long userId, SubscriptionStatus status, Long categoryId, String regionCode,
            LocalDateTime startTime, LocalDateTime endTime, Long id);

    /**
     * 이 매장 업장 알림 중, 빈자리 시작 시각을 알려줄 대상을 찾는다.
     * 해제한 알림과, 희망 종료 시각이 이미 지난 알림은 제외한다.
     * 빈자리 시작 시각이 희망 시작 이상이고 희망 종료 이하이면 포함한다.
     *
     * @param shopId        매장 ID
     * @param slotStartTime 빈자리 시작 시각
     * @param now           현재 시각
     * @return 알림 대상 목록
     */
    @Query(FIND_NOTIFIABLE_BY_SHOP)
    List<NotificationSubscription> findNotifiableByShop(@Param("shopId") Long shopId,
            @Param("slotStartTime") LocalDateTime slotStartTime, @Param("now") LocalDateTime now);

    /**
     * 카테고리와 지역이 같은 알림 중, 빈자리 시작 시각을 알려줄 대상을 찾는다.
     * 해제한 알림과, 희망 종료 시각이 이미 지난 알림은 제외한다.
     * 빈자리 시작 시각이 희망 시작 이상이고 희망 종료 이하이면 포함한다.
     *
     * @param categoryId    카테고리 ID
     * @param regionCode    지역코드
     * @param slotStartTime 빈자리 시작 시각
     * @param now           현재 시각
     * @return 알림 대상 목록
     */
    @Query(FIND_NOTIFIABLE_BY_CATEGORY_REGION)
    List<NotificationSubscription> findNotifiableByCategoryRegion(@Param("categoryId") Long categoryId,
            @Param("regionCode") String regionCode, @Param("slotStartTime") LocalDateTime slotStartTime,
            @Param("now") LocalDateTime now);

    /**
     * 한 회원의 사용 중인 알림을 모두 해제한다. 회원 탈퇴 뒤 빈자리 알림 대상이 되지 않게 한다.
     *
     * @param userId 회원 ID
     * @param now    수정 시각
     * @return 해제한 알림 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE NotificationSubscription s"
            + " SET s.status = com.ucamp.app.domain.notification.entity.SubscriptionStatus.INACTIVE, s.updatedAt = :now"
            + " WHERE s.userId = :userId AND s.status = " + ACTIVE)
    int deactivateAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
