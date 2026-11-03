package com.ucamp.app.domain.shop.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.domain.shop.policy.CustomerSegment;

/**
 * 고객 관리 목록의 고객 한 명. 이용 = 확정 예약 중 종료 시각이 지난 예약 (이용 추정).
 *
 * @param visitCount        이 매장 이용 횟수
 * @param totalAmount       이용한 예약의 적용가 합계 (예약 시점 가격 기준)
 * @param firstVisitAt      첫 이용 시작 시각. 이용이 없으면 null
 * @param lastVisitAt       최근 이용 시작 시각. 이용이 없으면 null
 * @param nextReservationAt 아직 끝나지 않은 가장 가까운 확정 예약 시작 시각. 없으면 null
 * @param segment           NEW(이용 0~1회) 또는 RETURNING(2회 이상)
 * @param hasMemo           매장 메모가 있는지
 */
public record ShopCustomerResponse(
        Long userId,
        String name,
        String phone,
        long visitCount,
        long totalAmount,
        OffsetDateTime firstVisitAt,
        OffsetDateTime lastVisitAt,
        OffsetDateTime nextReservationAt,
        CustomerSegment segment,
        boolean hasMemo) {
}
