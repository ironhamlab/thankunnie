package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 매장 대시보드·통계 집계용 예약 조회. 시술·취소·결제·예외 일정은 각 담당 도메인 소유라 엔티티 연관 없이 네이티브 쿼리로 읽는다.
 * 집계는 서비스에서 한다. 별칭은 대소문자를 지키도록 큰따옴표로 쓴다.
 */
public interface ReservationDashboardRepository extends Repository<Reservation, Long> {

    // 결제는 예약당 가장 최근 1건. 남은 예약금 = 납부 예약금 - 취소 환불액 (취소 완료 건만)
    @Query(value = """
            SELECT r.RESERVATION_ID AS "reservationId",
                   r.TREATMENT_ID AS "treatmentId",
                   t.NAME AS "treatmentName",
                   r.STATUS AS "status",
                   r.STARTS_AT AS "startsAt",
                   r.ENDS_AT AS "endsAt",
                   r.HOLD_EXPIRES_AT AS "holdExpiresAt",
                   r.CREATED_AT AS "createdAt",
                   r.APPLIED_PRICE AS "appliedPrice",
                   c.CANCEL_TYPE AS "cancelType",
                   c.COMPLETED_AT AS "cancelCompletedAt",
                   CASE WHEN r.STATUS = 'CANCELLED'
                        THEN COALESCE(p.AMOUNT, 0) - COALESCE(c.REFUND_AMOUNT, 0)
                        ELSE 0 END AS "retainedDeposit",
                   CASE WHEN EXISTS (
                       SELECT 1 FROM SHOP_SCHEDULE_EXCEPTION e
                       WHERE e.RESERVATION_ID = r.RESERVATION_ID AND e.EXCEPTION_TYPE = 'CLOSE')
                        THEN 1 ELSE 0 END AS "closedAfterCancel"
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            LEFT JOIN RESERVATION_CANCEL c ON c.RESERVATION_ID = r.RESERVATION_ID
            LEFT JOIN DEPOSIT_PAYMENT p ON p.DEPOSIT_PAYMENT_ID = (
                SELECT MAX(p2.DEPOSIT_PAYMENT_ID) FROM DEPOSIT_PAYMENT p2 WHERE p2.RESERVATION_ID = r.RESERVATION_ID)
            WHERE t.SHOP_ID = :shopId
              AND r.STATUS IN ('CONFIRMED', 'CANCELLED')
              AND r.STARTS_AT >= :startsFrom AND r.STARTS_AT < :startsBefore
            ORDER BY r.STARTS_AT, r.RESERVATION_ID
            """, nativeQuery = true)
    List<Row> findRowsRaw(@Param("shopId") Long shopId,
            @Param("startsFrom") LocalDateTime startsFrom, @Param("startsBefore") LocalDateTime startsBefore);

    /**
     * 시작 시각이 [startsFrom, startsBefore) 인 확정·취소 완료 예약을 시작 시각, 예약 ID 순으로 조회한다.
     */
    default List<ReservationDashboardRow> findRows(Long shopId, LocalDateTime startsFrom,
            LocalDateTime startsBefore) {
        return findRowsRaw(shopId, startsFrom, startsBefore).stream()
                .map(row -> new ReservationDashboardRow(
                        row.getReservationId(),
                        row.getTreatmentId(),
                        row.getTreatmentName(),
                        ReservationStatus.valueOf(row.getStatus()),
                        row.getStartsAt(),
                        row.getEndsAt(),
                        row.getHoldExpiresAt(),
                        row.getCreatedAt(),
                        toInt(row.getAppliedPrice()),
                        row.getCancelType() == null ? null : CancelType.valueOf(row.getCancelType()),
                        row.getCancelCompletedAt(),
                        toInt(row.getRetainedDeposit()),
                        toInt(row.getClosedAfterCancel()) == 1))
                .toList();
    }

    private static int toInt(Number value) {
        return value == null ? 0 : value.intValue();
    }

    /** 네이티브 쿼리 결과 프로젝션 */
    interface Row {

        Long getReservationId();

        Long getTreatmentId();

        String getTreatmentName();

        String getStatus();

        LocalDateTime getStartsAt();

        LocalDateTime getEndsAt();

        LocalDateTime getHoldExpiresAt();

        LocalDateTime getCreatedAt();

        Long getAppliedPrice();

        String getCancelType();

        LocalDateTime getCancelCompletedAt();

        Long getRetainedDeposit();

        Long getClosedAfterCancel();
    }
}
