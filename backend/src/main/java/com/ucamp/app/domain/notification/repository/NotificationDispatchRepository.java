package com.ucamp.app.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.entity.NotificationDispatchStatus;

/**
 * 발송 대기 작업 저장소.
 */
public interface NotificationDispatchRepository extends JpaRepository<NotificationDispatch, Long> {

    /**
     * 같은 이벤트·회원의 대기 작업이 이미 있는지 확인한다.
     *
     * @param eventId 알림 이벤트 ID
     * @param userId  회원 ID
     * @return 이미 있으면 true
     */
    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    /**
     * 해당 상태의 작업 중 ID가 가장 작은 하나를 조회한다.
     * ID가 작을수록 먼저 생긴 작업이다.
     *
     * @param status 작업 상태
     * @return 있으면 그 작업. 없으면 empty
     */
    Optional<NotificationDispatch> findFirstByStatusOrderByIdAsc(NotificationDispatchStatus status);

    /**
     * 아직 대기인 작업만 처리 중으로 바꾼다.
     * 다른 프로세스가 먼저 바꿨으면 조건이 맞지 않아 수정 건수는 0이다.
     * 조회해 둔 값은 옛 상태이므로, 수정 뒤에 DB 값을 다시 읽도록 영속성 컨텍스트를 비운다.
     *
     * @param dispatchId 발송 대기 작업 ID
     * @param pending    대기 상태
     * @param processing 처리 중 상태
     * @param now        수정 시각
     * @return 수정된 행 수. 성공하면 1, 이미 가져갔으면 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationDispatch d
            set d.status = :processing, d.updatedAt = :now
            where d.id = :dispatchId and d.status = :pending
            """)
    int claimPending(@Param("dispatchId") Long dispatchId,
            @Param("pending") NotificationDispatchStatus pending,
            @Param("processing") NotificationDispatchStatus processing,
            @Param("now") LocalDateTime now);
}
