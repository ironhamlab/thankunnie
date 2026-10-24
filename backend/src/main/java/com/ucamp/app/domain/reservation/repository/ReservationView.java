package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;

import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 예약 조회 결과 한 행. 예약·매장·시술·회원·최근 결제·취소를 합친 읽기 전용 값이다.
 * 결제·취소가 없으면 해당 필드는 null 이다.
 */
public record ReservationView(
        Long reservationId,
        ReservationStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        int appliedPrice,
        int deposit,
        LocalDateTime holdExpiresAt,
        LocalDateTime confirmedAt,
        String customerRequest,
        LocalDateTime createdAt,
        Long shopId,
        String shopName,
        Long treatmentId,
        String treatmentName,
        Long userId,
        String customerName,
        String customerPhone,
        String paymentStatus,
        Integer paymentAmount,
        Integer refundedAmount,
        LocalDateTime approvedAt,
        LocalDateTime refundedAt,
        String cancelType,
        String cancelStatus,
        Integer policyRefundAmount,
        Integer refundAmount,
        LocalDateTime cancelRequestedAt,
        LocalDateTime cancelCompletedAt) {
}
