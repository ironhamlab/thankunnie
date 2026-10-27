package com.ucamp.app.domain.cancel.repository;

import java.time.LocalDateTime;

import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 취소·환불 판단에 필요한 예약·매장·결제 정보. 결제 엔티티 없이 테이블을 직접 조회해 만든다.
 *
 * @param userId           예약한 고객 ID
 * @param shopId           예약한 시술의 매장 ID
 * @param heldAt           점유 시각 (예약 HOLD 생성 시각)
 * @param depositPaymentId 결제 완료(PAID)된 예약금결제 ID, 없으면 null. 환불(PaymentService.refund) 호출에 쓴다
 * @param paidAmount       결제 완료(PAID)된 예약금, 없으면 null
 * @param approvedAt       예약금 결제 승인 시각, 없으면 null
 * @param refundInProgress 환불 처리 중·환불된 결제가 있는지. 결제 완료 건이 없을 때 "결제 없음"과 구분한다
 */
public record CancelTarget(
        Long reservationId,
        Long userId,
        Long shopId,
        ReservationStatus status,
        LocalDateTime startsAt,
        LocalDateTime heldAt,
        Long depositPaymentId,
        Integer paidAmount,
        LocalDateTime approvedAt,
        boolean refundInProgress) {

    public boolean hasPayment() {
        return depositPaymentId != null && paidAmount != null && approvedAt != null;
    }
}
