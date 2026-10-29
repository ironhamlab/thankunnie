package com.ucamp.app.domain.reservation.repository;

/**
 * 취소로 다시 열린 시간에 들어온 확정 예약 집계.
 *
 * @param count 건수
 * @param sales 적용가 합계
 */
public record ReservationRebookStats(int count, long sales) {
}
