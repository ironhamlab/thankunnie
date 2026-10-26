package com.ucamp.app.domain.cancel.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 취소에 따른 예약 상태 변경 (CONFIRMED ↔ CANCEL_PENDING → CANCELLED). 확정·만료는 예약 담당(D)이 엔티티 메서드로 바꾼다.
 * 현재 상태를 조건으로 거는 조건부 갱신이라, 같은 예약에 동시에 들어온 취소는 한 건만 성공한다.
 * 벌크 갱신은 JPA 감사(updatedAt 자동 기록)를 거치지 않으므로 수정 시각을 직접 넘긴다.
 */
public interface CancelReservationRepository extends Repository<Reservation, Long> {

    /**
     * @return 바뀐 행 수. 0 이면 예약이 없거나 이미 다른 상태다
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Reservation r set r.status = :to, r.updatedAt = :now
            where r.id = :reservationId and r.status = :from
            """)
    int changeStatus(@Param("reservationId") Long reservationId, @Param("from") ReservationStatus from,
            @Param("to") ReservationStatus to, @Param("now") LocalDateTime now);
}
