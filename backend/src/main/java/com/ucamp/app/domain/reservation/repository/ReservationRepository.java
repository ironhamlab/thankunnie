package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.reservation.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 매장의 [startsAt, endsAt) 구간과 겹치고 취소·만료가 아닌 예약을 조회한다.
     * 점유(HOLD)의 만료 여부는 SalePolicy.isOccupying 으로 판단한다.
     * 시술 엔티티가 생기기 전까지 시술 → 매장 조인을 SQL 로 한다.
     */
    @Query(value = """
            SELECT r.* FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID = :shopId
              AND r.STATUS IN ('HOLD', 'CONFIRMED', 'CANCEL_PENDING')
              AND r.STARTS_AT < :endsAt
              AND r.ENDS_AT > :startsAt
            ORDER BY r.STARTS_AT
            """, nativeQuery = true)
    List<Reservation> findActiveOverlapping(@Param("shopId") Long shopId,
            @Param("startsAt") LocalDateTime startsAt, @Param("endsAt") LocalDateTime endsAt);

    /**
     * 사용자의 [startsAt, endsAt) 구간과 겹치고 취소·만료가 아닌 예약을 매장과 관계없이 조회한다.
     * 점유(HOLD)의 만료 여부는 SalePolicy.isOccupying 으로 판단한다.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.userId = :userId
              AND r.status IN (
                  com.ucamp.app.domain.reservation.entity.ReservationStatus.HOLD,
                  com.ucamp.app.domain.reservation.entity.ReservationStatus.CONFIRMED,
                  com.ucamp.app.domain.reservation.entity.ReservationStatus.CANCEL_PENDING)
              AND r.startsAt < :endsAt
              AND r.endsAt > :startsAt
            """)
    List<Reservation> findActiveOverlappingByUser(@Param("userId") Long userId,
            @Param("startsAt") LocalDateTime startsAt, @Param("endsAt") LocalDateTime endsAt);
}
