package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.common.policy.ReservationStatus;
import com.ucamp.app.domain.reservation.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 같은 매장에서 시간이 겹치는 예약을 조회한다. 종료 시각은 포함하지 않는다.
     * 점유(HOLD)의 만료 여부는 호출하는 쪽에서 SalePolicy.isOccupying 으로 판단한다.
     */
    @Query("""
            select r from Reservation r
            join r.treatment t
            where t.shopId = :shopId
              and r.status in :statuses
              and r.startsAt < :endsAt
              and r.endsAt > :startsAt
            """)
    List<Reservation> findOverlappingInShop(@Param("shopId") Long shopId,
            @Param("startsAt") LocalDateTime startsAt,
            @Param("endsAt") LocalDateTime endsAt,
            @Param("statuses") Collection<ReservationStatus> statuses);

    /**
     * 매장 행에 쓰기 잠금을 건다. 같은 매장의 예약 생성을 트랜잭션 단위로 순서대로 처리해
     * 겹침 검사와 저장 사이에 다른 예약이 끼어들지 않게 한다.
     * 매장 엔티티가 생기면 매장 Repository 의 비관적 잠금 조회로 옮긴다.
     */
    @Query(value = "SELECT SHOP_ID FROM SHOP WHERE SHOP_ID = :shopId FOR UPDATE", nativeQuery = true)
    Long lockShop(@Param("shopId") Long shopId);
}
