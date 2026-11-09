package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 예약 행을 잠가 조회한다 (SELECT FOR UPDATE). 확정끼리, 확정과 만료가 동시에 같은 예약을 바꾸지 않도록 줄 세운다.
     * 잠금은 호출한 트랜잭션이 끝날 때 풀린다.
     *
     * @param id 예약 ID
     * @return 잠근 예약, 없으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    /**
     * 만료 시각이 지난 점유(HOLD)를 한 번에 EXPIRED 로 바꾸고 바뀐 건수를 반환한다.
     * 상태 조건으로 갱신하므로 반복 실행해도 같은 예약을 두 번 바꾸지 않고, 확정 중인 행은 잠금이 풀린 뒤 조건을 다시 본다.
     * 일괄 갱신이라 영속성 컨텍스트를 거치지 않으므로 UPDATED_AT 을 함께 넣고, 실행 전후로 컨텍스트를 비운다.
     *
     * @param now 기준 시각. 점유 만료 시각이 이 시각과 같거나 이전이면 만료로 본다
     * @return EXPIRED 로 바뀐 예약 건수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Reservation r
            SET r.status = com.ucamp.app.domain.reservation.entity.ReservationStatus.EXPIRED,
                r.updatedAt = :now
            WHERE r.status = com.ucamp.app.domain.reservation.entity.ReservationStatus.HOLD
              AND r.holdExpiresAt <= :now
            """)
    int expireHolds(@Param("now") LocalDateTime now);

    /**
     * 현재 상태가 from 일 때만 to 로 바꾸고 바뀐 건수를 반환한다. 취소(E)의 CONFIRMED·CANCEL_PENDING·CANCELLED 전이에 쓴다.
     * 상태 조건으로 갱신하므로 같은 예약에 동시에 들어온 요청은 한 건만 성공한다(나머지는 0).
     * 일괄 갱신이라 UPDATED_AT 을 함께 넣는다. 같은 트랜잭션에서 먼저 조회한 다른 엔티티(취소 등)의 변경이
     * 사라지지 않도록 실행 후 영속성 컨텍스트는 비우지 않는다.
     *
     * @return 바뀐 행 수. 0 이면 예약이 없거나 이미 다른 상태다
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Reservation r
            SET r.status = :to, r.updatedAt = :now
            WHERE r.id = :reservationId AND r.status = :from
            """)
    int changeStatus(@Param("reservationId") Long reservationId, @Param("from") ReservationStatus from,
            @Param("to") ReservationStatus to, @Param("now") LocalDateTime now);

    /**
     * 확정 예약의 노쇼 여부를 from 일 때만 to 로 바꾸고 바뀐 건수를 반환한다. 종료 시각이 지난 예약만 바꾼다.
     * 조건으로 갱신하므로 같은 예약에 동시에 들어온 요청은 한 건만 성공한다(나머지는 0).
     * 취소는 시작 전에만 되고 노쇼는 종료 후에만 바뀌어 두 변경이 겹치지 않는다.
     *
     * @return 바뀐 행 수. 0 이면 예약이 없거나, 확정이 아니거나, 아직 안 끝났거나, 이미 to 상태다
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Reservation r
            SET r.noShow = :to, r.updatedAt = :now
            WHERE r.id = :reservationId AND r.status = :confirmed AND r.noShow = :from AND r.endsAt <= :now
            """)
    int changeNoShow(@Param("reservationId") Long reservationId, @Param("from") boolean from,
            @Param("to") boolean to, @Param("confirmed") ReservationStatus confirmed,
            @Param("now") LocalDateTime now);

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
     * findActiveOverlapping 의 여러 매장용. 빈자리 탐색이 매장·날짜마다 쿼리를 보내지 않도록 기간 전체를 한 번에 읽는다.
     */
    @Query(value = """
            SELECT t.SHOP_ID AS shopId, r.STATUS AS status, r.HOLD_EXPIRES_AT AS holdExpiresAt,
                   r.STARTS_AT AS startsAt, r.ENDS_AT AS endsAt
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID IN (:shopIds)
              AND r.STATUS IN ('HOLD', 'CONFIRMED', 'CANCEL_PENDING')
              AND r.STARTS_AT < :endsAt
              AND r.ENDS_AT > :startsAt
            """, nativeQuery = true)
    List<ReservationOccupancy> findActiveOccupancies(@Param("shopIds") Collection<Long> shopIds,
            @Param("startsAt") LocalDateTime startsAt, @Param("endsAt") LocalDateTime endsAt);

    /**
     * 사용자의 특정 상태 예약을 잠그지 않고 조회한다. 화면에 보여줄 현재 점유를 찾는 데 쓴다.
     * 점유(HOLD)의 만료 여부는 SalePolicy.isOccupying 으로 판단한다.
     */
    List<Reservation> findByUserIdAndStatus(Long userId, ReservationStatus status);

    /**
     * 사용자의 특정 상태 예약을 행을 잠가 조회한다 (SELECT FOR UPDATE). 1인 1점유 검사에서 HOLD 를 찾는 데 쓴다.
     * 같은 예약의 결제 확정과 줄 세워, 확정된 예약을 교체하면서 EXPIRED 로 덮어쓰지 않게 한다.
     * 점유(HOLD)의 만료 여부는 SalePolicy.isOccupying 으로 판단한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.userId = :userId AND r.status = :status")
    List<Reservation> findByUserIdAndStatusForUpdate(@Param("userId") Long userId,
            @Param("status") ReservationStatus status);

    /**
     * 사용자의 [startsAt, endsAt) 구간과 겹치고 취소·만료가 아닌 예약을 매장과 관계없이 조회한다.
     * 점유(HOLD)의 만료 여부는 SalePolicy.isOccupying 으로 판단한다.
     *
     * @param userId   회원 ID
     * @param startsAt 구간 시작 (포함)
     * @param endsAt   구간 종료 (미포함)
     * @return HOLD·CONFIRMED·CANCEL_PENDING 상태이면서 구간과 겹치는 본인 예약
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

    /**
     * 회원 탈퇴를 막는 진행 중 예약이 있는지 확인한다.
     * 만료 전 결제 대기(HOLD), 방문이 끝나지 않은 예약 완료(CONFIRMED), 환불이 끝나지 않은 취소 처리 중(CANCEL_PENDING)을 본다.
     * 결제 확정은 HOLD 에서만 일어나므로, HOLD 도 막아야 확정과 탈퇴가 겹쳐도 확정 예약이 남지 않는다.
     *
     * @param userId 회원 ID
     * @param now    기준 시각. 점유 만료 시각·종료 시각이 이보다 뒤면 진행 중이다
     * @return 하나라도 있으면 true
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.userId = :userId
              AND (r.status = com.ucamp.app.domain.reservation.entity.ReservationStatus.CANCEL_PENDING
                   OR r.status = com.ucamp.app.domain.reservation.entity.ReservationStatus.HOLD
                      AND r.holdExpiresAt > :now
                   OR r.status = com.ucamp.app.domain.reservation.entity.ReservationStatus.CONFIRMED
                      AND r.endsAt > :now)
            """)
    boolean existsInProgressByUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
