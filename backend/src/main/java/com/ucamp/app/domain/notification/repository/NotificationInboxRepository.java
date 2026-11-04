package com.ucamp.app.domain.notification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.notification.entity.NotificationInbox;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;

/**
 * 받은 알림 저장소.
 */
public interface NotificationInboxRepository extends JpaRepository<NotificationInbox, Long> {

    /**
     * 한 회원의 받은 알림을 최신 50건까지 조회한다.
     *
     * @param userId 회원 ID
     * @return 최신순 받은 알림. 최대 50건
     */
    List<NotificationInbox> findTop50ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /**
     * 한 회원의 받은 알림을 종류로 걸러 최신 50건까지 조회한다.
     *
     * @param userId   회원 ID
     * @param category 예약 또는 관심
     * @return 최신순 받은 알림. 최대 50건
     */
    List<NotificationInbox> findTop50ByUserIdAndCategoryOrderByCreatedAtDescIdDesc(
            Long userId, NotificationInboxCategory category);

    /**
     * 한 회원의 안 읽은 알림 수를 센다.
     *
     * @param userId 회원 ID
     * @return 읽은 시각이 없는 알림 수
     */
    long countByUserIdAndReadAtIsNull(Long userId);

    /**
     * 한 매장의 안 읽은 알림 수를 센다.
     *
     * @param shopId 매장 ID
     * @return 읽은 시각이 없는 알림 수
     */
    long countByShopIdAndReadAtIsNull(Long shopId);

    /**
     * 한 매장의 받은 알림을 최신 50건까지 조회한다.
     *
     * @param shopId 매장 ID
     * @return 최신순 받은 알림. 최대 50건
     */
    List<NotificationInbox> findTop50ByShopIdOrderByCreatedAtDescIdDesc(Long shopId);

    /**
     * 한 매장의 받은 알림을 종류로 걸러 최신 50건까지 조회한다.
     *
     * @param shopId   매장 ID
     * @param category 예약 또는 관심
     * @return 최신순 받은 알림. 최대 50건
     */
    List<NotificationInbox> findTop50ByShopIdAndCategoryOrderByCreatedAtDescIdDesc(
            Long shopId, NotificationInboxCategory category);

    /**
     * 한 매장의 안 읽은 알림을 조회한다.
     *
     * @param shopId 매장 ID
     * @return 읽은 시각이 없는 알림
     */
    List<NotificationInbox> findByShopIdAndReadAtIsNull(Long shopId);

    /**
     * 한 회원의 안 읽은 알림을 조회한다.
     *
     * @param userId 회원 ID
     * @return 읽은 시각이 없는 알림
     */
    List<NotificationInbox> findByUserIdAndReadAtIsNull(Long userId);

    /**
     * 한 회원의 받은 알림을 모두 지운다. 회원 탈퇴 때 쓴다.
     *
     * @param userId 회원 ID
     * @return 지운 알림 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM NotificationInbox i WHERE i.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
