package com.ucamp.app.domain.notification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.notification.entity.NotificationInbox;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;

/**
 * 받은 알림 저장소.
 */
public interface NotificationInboxRepository extends JpaRepository<NotificationInbox, Long> {

    /**
     * 한 회원의 받은 알림을 최신순으로 조회한다.
     *
     * @param userId 회원 ID
     * @return 받은 알림 목록
     */
    List<NotificationInbox> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /**
     * 한 회원의 받은 알림을 종류로 걸러 최신순으로 조회한다.
     *
     * @param userId   회원 ID
     * @param category 예약 또는 관심
     * @return 받은 알림 목록
     */
    List<NotificationInbox> findByUserIdAndCategoryOrderByCreatedAtDescIdDesc(
            Long userId, NotificationInboxCategory category);

    /**
     * 한 회원의 안 읽은 알림 수를 센다.
     *
     * @param userId 회원 ID
     * @return 읽은 시각이 없는 알림 수
     */
    long countByUserIdAndReadAtIsNull(Long userId);

    /**
     * 한 회원의 안 읽은 알림을 조회한다.
     *
     * @param userId 회원 ID
     * @return 읽은 시각이 없는 알림
     */
    List<NotificationInbox> findByUserIdAndReadAtIsNull(Long userId);
}
