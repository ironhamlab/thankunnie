package com.ucamp.app.domain.schedule.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
