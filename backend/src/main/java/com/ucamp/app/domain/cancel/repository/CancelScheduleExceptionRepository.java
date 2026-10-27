package com.ucamp.app.domain.cancel.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.schedule.entity.ShopScheduleException;

/**
 * 사장님 취소의 "닫기" 선택 기록 (E-07, REO-02). 예약 ID 가 있는 CLOSE 예외 일정만 다룬다.
 * 예외 일정 엔티티는 일정 담당(B)의 것을 쓰고, 취소 쪽에서 필요한 조회·삭제만 이 리포지토리에 둔다.
 */
public interface CancelScheduleExceptionRepository extends Repository<ShopScheduleException, Long> {

    ShopScheduleException save(ShopScheduleException scheduleException);

    boolean existsByReservationId(Long reservationId);

    /**
     * 결제 취소가 실패해 예약을 확정으로 되돌릴 때 취소하며 등록한 닫기를 지운다.
     * 같은 트랜잭션에서 먼저 조회한 엔티티가 분리되지 않도록 영속성 컨텍스트는 비우지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from ShopScheduleException e where e.reservationId = :reservationId")
    int deleteByReservationId(@Param("reservationId") Long reservationId);
}
