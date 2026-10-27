package com.ucamp.app.domain.cancel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.cancel.entity.ReservationCancel;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 취소 대상 조회. 예약(D)·결제(C) 엔티티 구조에 묶이지 않도록 예약 → 시술(매장), 결제를 네이티브 쿼리 하나로 읽는다.
 * 결제는 결제 완료(PAID) 건만 본다. 결제 취소 실패로 확정 상태로 되돌린 예약은 결제도 PAID 로 돌아와 있어야 한다.
 * 한 예약에 결제 완료가 여러 건이면 이중 결제 사고다. 하나를 골라 견적을 내면 환불 누락을 숨기게 되므로 예외로 막는다.
 */
public interface CancelTargetRepository extends Repository<ReservationCancel, Long> {

    @Query(value = """
            SELECT r.RESERVATION_ID AS "reservationId",
                   r.USER_ID AS "userId",
                   t.SHOP_ID AS "shopId",
                   r.STATUS AS "status",
                   r.STARTS_AT AS "startsAt",
                   r.CREATED_AT AS "heldAt",
                   p.DEPOSIT_PAYMENT_ID AS "depositPaymentId",
                   p.AMOUNT AS "paidAmount",
                   p.APPROVED_AT AS "approvedAt",
                   (SELECT COUNT(*) FROM DEPOSIT_PAYMENT x
                     WHERE x.RESERVATION_ID = r.RESERVATION_ID
                       AND x.STATUS IN ('REFUND_PENDING', 'PART_REFUNDED', 'REFUNDED')) AS "refundingPaymentCount"
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            LEFT JOIN DEPOSIT_PAYMENT p ON p.RESERVATION_ID = r.RESERVATION_ID AND p.STATUS = 'PAID'
            WHERE r.RESERVATION_ID = :reservationId
            """, nativeQuery = true)
    List<CancelTargetRow> findRowsByReservationId(@Param("reservationId") Long reservationId);

    default Optional<CancelTarget> findByReservationId(Long reservationId) {
        List<CancelTargetRow> rows = findRowsByReservationId(reservationId);
        if (rows.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(
                    "예약 " + reservationId + " 에 결제 완료(PAID) 건이 " + rows.size() + "건 있습니다", 1, rows.size());
        }
        return rows.stream().findFirst().map(CancelTargetRepository::toTarget);
    }

    private static CancelTarget toTarget(CancelTargetRow row) {
        Long refundingPaymentCount = row.getRefundingPaymentCount();
        return new CancelTarget(
                row.getReservationId(),
                row.getUserId(),
                row.getShopId(),
                ReservationStatus.valueOf(row.getStatus()),
                row.getStartsAt(),
                row.getHeldAt(),
                row.getDepositPaymentId(),
                row.getPaidAmount(),
                row.getApprovedAt(),
                refundingPaymentCount != null && refundingPaymentCount > 0);
    }
}
