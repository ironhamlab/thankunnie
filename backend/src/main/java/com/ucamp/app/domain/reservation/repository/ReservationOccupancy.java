package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;

import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 가용 시간 계산에 필요한 예약의 값만 담은 조회 결과. 여러 매장을 한 번에 읽을 때 매장별로 나누려고 매장 ID 를 함께 둔다.
 */
public interface ReservationOccupancy {

    Long getShopId();

    ReservationStatus getStatus();

    LocalDateTime getHoldExpiresAt();

    LocalDateTime getStartsAt();

    LocalDateTime getEndsAt();
}
