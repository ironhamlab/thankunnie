package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 매장 대시보드·통계 집계용 예약 조회. 시술·취소·결제·예외 일정·구독은 각 담당 도메인 소유라 엔티티 연관 없이 네이티브 쿼리로 읽는다.
 * 집계는 서비스에서 한다. 별칭은 대소문자를 지키도록 큰따옴표로 쓴다.
 */
public interface ReservationDashboardRepository extends Repository<Reservation, Long> {

    // 결제는 예약당 가장 최근 1건. 남은 예약금 = 납부 예약금 - 취소 환불액 (취소 완료 건만)
    @Query(value = """
            SELECT r.RESERVATION_ID AS "reservationId",
                   r.USER_ID AS "userId",
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
                        THEN 1 ELSE 0 END AS "closedAfterCancel",
                   CASE WHEN r.NO_SHOW = 'Y' THEN 1 ELSE 0 END AS "noShow"
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
                        row.getUserId(),
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
                        toInt(row.getClosedAfterCancel()) == 1,
                        toInt(row.getNoShow()) == 1))
                .toList();
    }

    /**
     * 종료 시각이 endsUntil 이하이고 노쇼가 아닌 확정 예약(이용 추정)의 고객과 종료 시각. 신규·재방문을 전체 이력 기준으로 나눌 때 쓴다.
     */
    @Query(value = """
            SELECT r.USER_ID AS "userId", r.ENDS_AT AS "endsAt"
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID = :shopId
              AND r.STATUS = 'CONFIRMED'
              AND r.NO_SHOW = 'N'
              AND r.ENDS_AT <= :endsUntil
            """, nativeQuery = true)
    List<Visit> findVisits(@Param("shopId") Long shopId, @Param("endsUntil") LocalDateTime endsUntil);

    /**
     * 매장 가입 시각과 첫 확정·취소 완료 예약 시작 시각. "서비스 이용 이후 전체" 조회의 시작 날짜를 정한다.
     */
    @Query(value = """
            SELECT s.CREATED_AT AS "shopCreatedAt",
                   (SELECT MIN(r.STARTS_AT)
                    FROM RESERVATION r
                    JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
                    WHERE t.SHOP_ID = s.SHOP_ID
                      AND r.STATUS IN ('CONFIRMED', 'CANCELLED')) AS "firstStartsAt"
            FROM SHOP s
            WHERE s.SHOP_ID = :shopId
            """, nativeQuery = true)
    Optional<ServiceStart> findServiceStart(@Param("shopId") Long shopId);

    /** 이 매장을 찜(업장 구독)한 고객 수. 지역·카테고리 구독은 특정 매장을 고른 것이 아니라 세지 않는다 */
    @Query(value = """
            SELECT COUNT(DISTINCT n.USER_ID)
            FROM NOTIFICATION_SUBSCRIPTION n
            WHERE n.SHOP_ID = :shopId
              AND n.SUBSCRIPTION_TYPE = 'STORE'
              AND n.STATUS = 'ACTIVE'
            """, nativeQuery = true)
    Number countFavoriteCustomersRaw(@Param("shopId") Long shopId);

    default long countFavoriteCustomers(Long shopId) {
        Number count = countFavoriteCustomersRaw(shopId);
        return count == null ? 0 : count.longValue();
    }

    private static int toInt(Number value) {
        return value == null ? 0 : value.intValue();
    }

    /** 네이티브 쿼리 결과 프로젝션 */
    interface Row {

        Long getReservationId();

        Long getUserId();

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

        Long getNoShow();
    }

    /** 이용 추정 예약 한 건 */
    interface Visit {

        Long getUserId();

        LocalDateTime getEndsAt();
    }

    interface ServiceStart {

        LocalDateTime getShopCreatedAt();

        LocalDateTime getFirstStartsAt();
    }
}
