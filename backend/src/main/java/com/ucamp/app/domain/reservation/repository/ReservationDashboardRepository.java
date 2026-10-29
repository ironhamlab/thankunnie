package com.ucamp.app.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 사장님 메인 대시보드 집계. 예약·시술·취소는 각 담당 도메인 소유라 엔티티 없이 읽기 전용 SQL 로 센다.
 * 모든 집계는 시술일(STARTS_AT) 기준이며 구간은 [시작, 끝)이다.
 */
@Repository
public class ReservationDashboardRepository {

    private static final String RESERVATION_COUNTS_SQL = """
            SELECT COALESCE(SUM(CASE WHEN r.STATUS = 'CONFIRMED'
                                      AND r.STARTS_AT >= :dayStart AND r.STARTS_AT < :dayEnd
                                     THEN 1 ELSE 0 END), 0) AS TODAY_CONFIRMED,
                   COALESCE(SUM(CASE WHEN r.STATUS = 'CONFIRMED'
                                      AND r.STARTS_AT > :now AND r.STARTS_AT < :dayEnd
                                     THEN 1 ELSE 0 END), 0) AS TODAY_UPCOMING,
                   COALESCE(SUM(CASE WHEN r.STATUS = 'CONFIRMED' THEN 1 ELSE 0 END), 0) AS CONFIRMED,
                   COALESCE(SUM(CASE WHEN r.STATUS = 'CONFIRMED' THEN r.APPLIED_PRICE ELSE 0 END), 0) AS EXPECTED_SALES,
                   COALESCE(SUM(CASE WHEN r.STATUS = 'CANCEL_PENDING' THEN 1 ELSE 0 END), 0) AS CANCEL_PENDING
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID = :shopId
              AND r.STARTS_AT >= :periodStart AND r.STARTS_AT < :periodEnd
            """;

    // 결제 취소가 끝난 예약만 센다. 결제 취소에 실패하면 예약이 CONFIRMED 로 돌아가므로 예약 상태로 거른다
    private static final String CANCEL_COUNTS_SQL = """
            SELECT c.CANCEL_TYPE, COUNT(*) AS CNT
            FROM RESERVATION_CANCEL c
            JOIN RESERVATION r ON r.RESERVATION_ID = c.RESERVATION_ID
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID = :shopId
              AND r.STATUS = 'CANCELLED'
              AND r.STARTS_AT >= :periodStart AND r.STARTS_AT < :periodEnd
            GROUP BY c.CANCEL_TYPE
            """;

    // 재판매: 같은 매장의 취소 완료 시간과 겹치고, 그 취소가 끝난 뒤 생성된 확정 예약
    private static final String REBOOKED_SQL = """
            SELECT COUNT(*) AS CNT, COALESCE(SUM(r.APPLIED_PRICE), 0) AS SALES
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
            """;

    // 통계 화면용 원본 행. 날짜·요일·시간대 묶음은 DB 방언 차이를 피하려고 서비스에서 계산한다
    private static final String STATS_ROWS_SQL = """
            SELECT r.TREATMENT_ID, t.NAME AS TREATMENT_NAME, r.STATUS, r.STARTS_AT, r.HOLD_EXPIRES_AT,
                   r.APPLIED_PRICE,
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
                   THEN 1 ELSE 0 END AS REBOOKED
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID = :shopId
              AND r.STATUS IN ('CONFIRMED', 'CANCELLED')
              AND r.STARTS_AT >= :periodStart AND r.STARTS_AT < :periodEnd
            ORDER BY r.STARTS_AT, r.RESERVATION_ID
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReservationDashboardRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 기간 안 예약 건수와 오늘 확정·남은 확정 건수. 오늘은 기간 안에 있어야 한다.
     */
    public ReservationDashboardCounts countReservations(Long shopId, LocalDateTime periodStart,
            LocalDateTime periodEnd, LocalDateTime dayStart, LocalDateTime now) {
        MapSqlParameterSource params = period(shopId, periodStart, periodEnd)
                .addValue("dayStart", dayStart)
                .addValue("dayEnd", dayStart.plusDays(1))
                .addValue("now", now);
        return jdbcTemplate.queryForObject(RESERVATION_COUNTS_SQL, params, (rs, rowNum) ->
                new ReservationDashboardCounts(
                        rs.getInt("TODAY_CONFIRMED"),
                        rs.getInt("TODAY_UPCOMING"),
                        rs.getInt("CONFIRMED"),
                        rs.getLong("EXPECTED_SALES"),
                        rs.getInt("CANCEL_PENDING")));
    }

    /**
     * 기간 안 취소 완료 건수를 취소 유형별로 센다. 건이 없는 유형은 0 이다.
     */
    public Map<CancelType, Integer> countCancelledByType(Long shopId, LocalDateTime periodStart,
            LocalDateTime periodEnd) {
        Map<CancelType, Integer> counts = new EnumMap<>(CancelType.class);
        for (CancelType type : CancelType.values()) {
            counts.put(type, 0);
        }
        jdbcTemplate.query(CANCEL_COUNTS_SQL, period(shopId, periodStart, periodEnd), rs -> {
            counts.put(CancelType.valueOf(rs.getString("CANCEL_TYPE")), rs.getInt("CNT"));
        });
        return counts;
    }

    /**
     * 기간 안 확정 예약 중 취소로 다시 열린 시간에 들어온 건수와 적용가 합계.
     */
    public ReservationRebookStats countRebooked(Long shopId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        return jdbcTemplate.queryForObject(REBOOKED_SQL, period(shopId, periodStart, periodEnd), (rs, rowNum) ->
                new ReservationRebookStats(rs.getInt("CNT"), rs.getLong("SALES")));
    }

    /**
     * 기간 안 확정·취소 완료 예약을 통계용 행으로 조회한다. 재판매 여부는 대시보드 재판매 판정과 같다.
     */
    public List<ReservationStatsRow> findStatsRows(Long shopId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        return jdbcTemplate.query(STATS_ROWS_SQL, period(shopId, periodStart, periodEnd), (rs, rowNum) ->
                new ReservationStatsRow(
                        rs.getLong("TREATMENT_ID"),
                        rs.getString("TREATMENT_NAME"),
                        ReservationStatus.valueOf(rs.getString("STATUS")),
                        rs.getObject("STARTS_AT", LocalDateTime.class),
                        rs.getObject("HOLD_EXPIRES_AT", LocalDateTime.class),
                        rs.getInt("APPLIED_PRICE"),
                        rs.getInt("REBOOKED") == 1));
    }

    private static MapSqlParameterSource period(Long shopId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        return new MapSqlParameterSource()
                .addValue("shopId", shopId)
                .addValue("periodStart", periodStart)
                .addValue("periodEnd", periodEnd);
    }
}
