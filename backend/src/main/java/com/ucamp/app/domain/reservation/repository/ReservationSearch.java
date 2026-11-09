package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.Set;

import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 예약 조회 조건. null 인 조건은 적용하지 않는다.
 *
 * @param shopId       매장 (매장 계정 조회)
 * @param userId       예약 고객 (고객 조회)
 * @param statuses     포함할 예약 상태. 비어 있으면 안 된다
 * @param startsFrom   시작 시각 하한 (포함)
 * @param startsBefore 시작 시각 상한 (미포함)
 * @param endsAfter    종료 시각 하한 (미포함)
 * @param endsUntil    종료 시각 상한 (포함)
 */
public record ReservationSearch(
        Long shopId,
        Long userId,
        Set<ReservationStatus> statuses,
        LocalDateTime startsFrom,
        LocalDateTime startsBefore,
        LocalDateTime endsAfter,
        LocalDateTime endsUntil) {

    public ReservationSearch(Long shopId, Long userId, Set<ReservationStatus> statuses, LocalDateTime startsFrom,
            LocalDateTime startsBefore) {
        this(shopId, userId, statuses, startsFrom, startsBefore, null, null);
    }
}
