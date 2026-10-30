package com.ucamp.app.domain.schedule.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;
import com.ucamp.app.domain.schedule.entity.ShopScheduleException;

public interface ShopScheduleExceptionRepository extends JpaRepository<ShopScheduleException, Long> {

    /**
     * 매장의 [from, to) 구간과 겹치는 열기·닫기 일정을 시작 시각 순으로 조회한다.
     */
    @Query("""
            SELECT e FROM ShopScheduleException e
            WHERE e.shopId = :shopId
              AND e.startsAt < :to
              AND e.endsAt > :from
            ORDER BY e.startsAt
            """)
    List<ShopScheduleException> findOverlapping(@Param("shopId") Long shopId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 매장에 [startsAt, endsAt) 와 겹치는 같은 종류(열기·닫기)의 일정이 있는지. 수정할 때는 자기 자신(excludeId)을 뺀다.
     */
    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
            FROM ShopScheduleException e
            WHERE e.shopId = :shopId
              AND e.exceptionType = :type
              AND e.startsAt < :endsAt
              AND e.endsAt > :startsAt
              AND (:excludeId IS NULL OR e.id <> :excludeId)
            """)
    boolean existsSameTypeOverlapping(@Param("shopId") Long shopId, @Param("type") ScheduleExceptionType type,
            @Param("startsAt") LocalDateTime startsAt, @Param("endsAt") LocalDateTime endsAt,
            @Param("excludeId") Long excludeId);

    /**
     * 사장님이 예약을 취소하며 그 시간을 닫았는지(예약 ID 가 있는 일정이 있는지). 취소 완료 시 재공개 이벤트 발행 여부를 정한다.
     */
    boolean existsByReservationId(Long reservationId);

    /**
     * 예약을 취소하며 등록한 닫기를 지운다. 결제 취소가 실패해 예약을 확정으로 되돌릴 때 쓴다.
     * 같은 트랜잭션에서 먼저 조회한 다른 엔티티가 분리되지 않도록 영속성 컨텍스트는 비우지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ShopScheduleException e WHERE e.reservationId = :reservationId")
    int deleteByReservationId(@Param("reservationId") Long reservationId);
}
