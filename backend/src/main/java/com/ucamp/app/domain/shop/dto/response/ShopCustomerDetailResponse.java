package com.ucamp.app.domain.shop.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.ucamp.app.domain.cancel.entity.CancelType;

/**
 * 고객 상세: 목록과 같은 요약, 매장 메모, 이 매장 예약 이력(최근 50건).
 *
 * @param memo         매장 메모. 없으면 null
 * @param reservations 확정·취소 완료 예약을 최근 시작 순으로
 */
public record ShopCustomerDetailResponse(
        ShopCustomerResponse customer,
        ShopCustomerMemoResponse memo,
        List<History> reservations) {

    public enum HistoryStatus {
        /** 확정 예약 중 종료 시각이 지난 예약 (이용 추정) */
        COMPLETED,
        /** 아직 끝나지 않은 확정 예약 */
        UPCOMING,
        /** 매장이 노쇼로 표시한 확정 예약 */
        NO_SHOW,
        /** 취소 완료 */
        CANCELLED
    }

    /**
     * @param appliedPrice    예약 시점 적용가
     * @param cancelType      취소 유형. 취소가 아니면 null
     * @param customerRequest 고객이 예약할 때 남긴 요청사항
     */
    public record History(
            Long reservationId,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String treatmentName,
            long appliedPrice,
            HistoryStatus status,
            CancelType cancelType,
            String customerRequest) {
    }
}
