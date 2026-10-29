package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 사장님 대시보드·통계 집계. 시술·취소는 각 담당 도메인 소유라 엔티티 연관 없이 네이티브 쿼리로 읽는다.
 * 모든 집계는 시술일(STARTS_AT) 기준이며 구간은 [시작, 끝)이다. 별칭은 대소문자를 지키도록 큰따옴표로 쓴다.
 */
public interface ReservationDashboardRepository extends Repository<Reservation, Long> {

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN r.STATUS = 'CONFIRMED'
                                      AND r.STARTS_AT >= :dayStart AND r.STARTS_AT < :dayEnd
                                     THEN 1 ELSE 0 END), 0) AS "todayConfirmedCount",
                   COALESCE(SUM(CASE WHEN r.STATUS = 'CONFIRMED'
                                      AND r.STARTS_AT > :now AND r.STARTS_AT < :dayEnd
                                     THEN 1 ELSE 0 END), 0) AS "todayUpcomingCount",
                   COALESCE(SUM(CASE WHEN r.STATUS = 'CONFIRMED' THEN 1 ELSE 0 END), 0) AS "confirmedCount",
                   COALESCE(SUM(CASE WHEN r.STATUS = 'CONFIRMED' THEN r.APPLIED_PRICE ELSE 0 END), 0) AS "expectedSales",
                   COALESCE(SUM(CASE WHEN r.STATUS = 'CANCEL_PENDING' THEN 1 ELSE 0 END), 0) AS "cancelPendingCount"
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID = :shopId
              AND r.STARTS_AT >= :periodStart AND r.STARTS_AT < :periodEnd
            """, nativeQuery = true)
    CountsRow findCountsRow(@Param("shopId") Long shopId,
            @Param("periodStart") LocalDateTime periodStart, @Param("periodEnd") LocalDateTime periodEnd,
            @Param("dayStart") LocalDateTime dayStart, @Param("dayEnd") LocalDateTime dayEnd,
            @Param("now") LocalDateTime now);

    // 결제 취소가 끝난 예약만 센다. 결제 취소에 실패하면 예약이 CONFIRMED 로 돌아가므로 예약 상태로 거른다
    @Query(value = """
            SELECT c.CANCEL_TYPE AS "cancelType", COUNT(*) AS "count"
            FROM RESERVATION_CANCEL c
            JOIN RESERVATION r ON r.RESERVATION_ID = c.RESERVATION_ID
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID = :shopId
              AND r.STATUS = 'CANCELLED'
              AND r.STARTS_AT >= :periodStart AND r.STARTS_AT < :periodEnd
            GROUP BY c.CANCEL_TYPE
            """, nativeQuery = true)
    List<CancelTypeCountRow> findCancelTypeCountRows(@Param("shopId") Long shopId,
            @Param("periodStart") LocalDateTime periodStart, @Param("periodEnd") LocalDateTime periodEnd);

    // 재판매: 같은 매장의 취소 완료 시간과 겹치고, 그 취소가 끝난 뒤 생성된 확정 예약
    @Query(value = """
            SELECT COUNT(*) AS "count", COALESCE(SUM(r.APPLIED_PRICE), 0) AS "sales"
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID = :shopId
              AND r.STATUS = 'CONFIRMED'
              AND r.STARTS_AT >= :periodStart AND r.STARTS_AT < :periodEnd
              AND EXISTS (
                  SELECT 1
                  FROM RESERVATION rc
                  JOIN TREATMENT tc ON tc.TREATMENT_ID = rc.TREATMENT_ID
                  JOIN RESERVATION_CANCEL c ON c.RESERVATION_ID = rc.RESERVATION_ID
                  WHERE tc.SHOP_ID = t.SHOP_ID
                    AND rc.STATUS = 'CANCELLED'
                    AND c.COMPLETED_AT IS NOT NULL
                    AND r.CREATED_AT >= c.COMPLETED_AT
                    AND rc.STARTS_AT < r.ENDS_AT AND r.STARTS_AT < rc.ENDS_AT)
            """, nativeQuery = true)
    RebookRow findRebookRow(@Param("shopId") Long shopId,
            @Param("periodStart") LocalDateTime periodStart, @Param("periodEnd") LocalDateTime periodEnd);

    // 통계 화면용 원본 행. 날짜·요일·시간대 묶음은 DB 방언 차이를 피하려고 서비스에서 계산한다
    @Query(value = """
            SELECT r.TREATMENT_ID AS "treatmentId", t.NAME AS "treatmentName", r.STATUS AS "status",
                   r.STARTS_AT AS "startsAt", r.HOLD_EXPIRES_AT AS "holdExpiresAt",
                   r.APPLIED_PRICE AS "appliedPrice",
                   CASE WHEN r.STATUS = 'CONFIRMED' AND EXISTS (
                       SELECT 1
                       FROM RESERVATION rc
                       JOIN TREATMENT tc ON tc.TREATMENT_ID = rc.TREATMENT_ID
                       JOIN RESERVATION_CANCEL c ON c.RESERVATION_ID = rc.RESERVATION_ID
                       WHERE tc.SHOP_ID = t.SHOP_ID
                         AND rc.STATUS = 'CANCELLED'
                         AND c.COMPLETED_AT IS NOT NULL
                         AND r.CREATED_AT >= c.COMPLETED_AT
                         AND rc.STARTS_AT < r.ENDS_AT AND r.STARTS_AT < rc.ENDS_AT)
                   THEN 1 ELSE 0 END AS "rebooked"
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID = :shopId
              AND r.STATUS IN ('CONFIRMED', 'CANCELLED')
              AND r.STARTS_AT >= :periodStart AND r.STARTS_AT < :periodEnd
            ORDER BY r.STARTS_AT, r.RESERVATION_ID
            """, nativeQuery = true)
    List<StatsRow> findStatsRowsRaw(@Param("shopId") Long shopId,
            @Param("periodStart") LocalDateTime periodStart, @Param("periodEnd") LocalDateTime periodEnd);

    /**
     * 기간 안 예약 건수와 오늘 확정·남은 확정 건수. 오늘은 기간 안에 있어야 한다.
     */
    default ReservationDashboardCounts countReservations(Long shopId, LocalDateTime periodStart,
            LocalDateTime periodEnd, LocalDateTime dayStart, LocalDateTime now) {
        CountsRow row = findCountsRow(shopId, periodStart, periodEnd, dayStart, dayStart.plusDays(1), now);
        return new ReservationDashboardCounts(
                toInt(row.getTodayConfirmedCount()),
                toInt(row.getTodayUpcomingCount()),
                toInt(row.getConfirmedCount()),
                toLong(row.getExpectedSales()),
                toInt(row.getCancelPendingCount()));
    }

    /**
     * 기간 안 취소 완료 건수를 취소 유형별로 센다. 건이 없는 유형은 0 이다.
     */
    default Map<CancelType, Integer> countCancelledByType(Long shopId, LocalDateTime periodStart,
            LocalDateTime periodEnd) {
        Map<CancelType, Integer> counts = new EnumMap<>(CancelType.class);
        for (CancelType type : CancelType.values()) {
            counts.put(type, 0);
        }
        for (CancelTypeCountRow row : findCancelTypeCountRows(shopId, periodStart, periodEnd)) {
            counts.put(CancelType.valueOf(row.getCancelType()), toInt(row.getCount()));
        }
        return counts;
    }

    /**
     * 기간 안 확정 예약 중 취소로 다시 열린 시간에 들어온 건수와 적용가 합계.
     */
    default ReservationRebookStats countRebooked(Long shopId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        RebookRow row = findRebookRow(shopId, periodStart, periodEnd);
        return new ReservationRebookStats(toInt(row.getCount()), toLong(row.getSales()));
    }

    /**
     * 기간 안 확정·취소 완료 예약을 통계용 행으로 조회한다. 재판매 여부는 대시보드 재판매 판정과 같다.
     */
    default List<ReservationStatsRow> findStatsRows(Long shopId, LocalDateTime periodStart,
            LocalDateTime periodEnd) {
        return findStatsRowsRaw(shopId, periodStart, periodEnd).stream()
                .map(row -> new ReservationStatsRow(
                        row.getTreatmentId(),
                        row.getTreatmentName(),
                        ReservationStatus.valueOf(row.getStatus()),
                        row.getStartsAt(),
                        row.getHoldExpiresAt(),
                        toInt(row.getAppliedPrice()),
                        toInt(row.getRebooked()) == 1))
                .toList();
    }

    private static int toInt(Number value) {
        return value == null ? 0 : value.intValue();
    }

    private static long toLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    /** 예약 건수 집계 프로젝션 */
    interface CountsRow {

        Long getTodayConfirmedCount();

        Long getTodayUpcomingCount();

        Long getConfirmedCount();

        Long getExpectedSales();

        Long getCancelPendingCount();
    }

    /** 취소 유형별 건수 프로젝션 */
    interface CancelTypeCountRow {

        String getCancelType();

        Long getCount();
    }

    /** 재판매 집계 프로젝션 */
    interface RebookRow {

        Long getCount();

        Long getSales();
    }

    /** 통계 원본 행 프로젝션 */
    interface StatsRow {

        Long getTreatmentId();

        String getTreatmentName();

        String getStatus();

        LocalDateTime getStartsAt();

        LocalDateTime getHoldExpiresAt();

        Long getAppliedPrice();

        Long getRebooked();
    }
}
