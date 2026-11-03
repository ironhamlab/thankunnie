package com.ucamp.app.domain.reservation.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 역할별 예약 조회 (B-09·B-10·B-11). 매장·시술·회원·결제·취소는 각 담당 도메인 소유라 엔티티 없이 읽기 전용 SQL 로 조회한다.
 * 결제는 예약당 가장 최근 결제 1건을 붙인다. 정렬은 시작 시각, 예약 ID 순이다.
 */
@Repository
public class ReservationQueryRepository {

    private static final String SELECT_COLUMNS = """
            SELECT r.RESERVATION_ID, r.STATUS, r.STARTS_AT, r.ENDS_AT, r.APPLIED_PRICE, r.DEPOSIT,
                   r.HOLD_EXPIRES_AT, r.CONFIRMED_AT, r.CUSTOMER_REQUEST, r.CREATED_AT,
                   s.SHOP_ID, s.NAME AS SHOP_NAME, t.TREATMENT_ID, t.NAME AS TREATMENT_NAME,
                   u.USER_ID, u.NAME AS CUSTOMER_NAME,
                   -- 탈퇴 회원 번호는 재가입 안내용으로 DB 에만 남기고 매장 화면에는 보내지 않는다
                   CASE WHEN u.STATUS = 'INACTIVE' THEN NULL ELSE u.PHONE END AS CUSTOMER_PHONE,
                   p.STATUS AS PAYMENT_STATUS, p.AMOUNT AS PAYMENT_AMOUNT, p.REFUNDED_AMOUNT,
                   p.APPROVED_AT, p.REFUNDED_AT,
                   c.CANCEL_TYPE, c.STATUS AS CANCEL_STATUS, c.POLICY_REFUND_AMOUNT, c.REFUND_AMOUNT,
                   c.REQUESTED_AT AS CANCEL_REQUESTED_AT, c.COMPLETED_AT AS CANCEL_COMPLETED_AT
            """;

    private static final String FROM_JOINS = """
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            JOIN SHOP s ON s.SHOP_ID = t.SHOP_ID
            JOIN APP_USER u ON u.USER_ID = r.USER_ID
            LEFT JOIN DEPOSIT_PAYMENT p ON p.DEPOSIT_PAYMENT_ID = (
                SELECT MAX(p2.DEPOSIT_PAYMENT_ID) FROM DEPOSIT_PAYMENT p2 WHERE p2.RESERVATION_ID = r.RESERVATION_ID)
            LEFT JOIN RESERVATION_CANCEL c ON c.RESERVATION_ID = r.RESERVATION_ID
            """;

    private static final String ORDER_BY = " ORDER BY r.STARTS_AT, r.RESERVATION_ID";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReservationQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReservationView> search(ReservationSearch search, long offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
        String sql = SELECT_COLUMNS + FROM_JOINS + where(search, params) + ORDER_BY
                + " OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    public long count(ReservationSearch search) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT COUNT(*) " + FROM_JOINS + where(search, params);
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 예약 한 건을 조회한다. shopId·userId 가 있으면 그 범위 안에서만 찾는다 (다른 매장·고객 예약은 없는 것으로 본다).
     */
    public Optional<ReservationView> findOne(Long reservationId, Long shopId, Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("reservationId", reservationId);
        List<String> conditions = new ArrayList<>(List.of("r.RESERVATION_ID = :reservationId"));
        addScope(conditions, params, shopId, userId);
        String sql = SELECT_COLUMNS + FROM_JOINS + " WHERE " + String.join(" AND ", conditions);
        return jdbcTemplate.query(sql, params, ROW_MAPPER).stream().findFirst();
    }

    private static String where(ReservationSearch search, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        addScope(conditions, params, search.shopId(), search.userId());
        conditions.add("r.STATUS IN (:statuses)");
        params.addValue("statuses", search.statuses().stream().map(Enum::name).toList());
        if (search.startsFrom() != null) {
            conditions.add("r.STARTS_AT >= :startsFrom");
            params.addValue("startsFrom", search.startsFrom());
        }
        if (search.startsBefore() != null) {
            conditions.add("r.STARTS_AT < :startsBefore");
            params.addValue("startsBefore", search.startsBefore());
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    private static void addScope(List<String> conditions, MapSqlParameterSource params, Long shopId, Long userId) {
        if (shopId != null) {
            conditions.add("s.SHOP_ID = :shopId");
            params.addValue("shopId", shopId);
        }
        if (userId != null) {
            conditions.add("r.USER_ID = :userId");
            params.addValue("userId", userId);
        }
    }

    private static final RowMapper<ReservationView> ROW_MAPPER = (rs, rowNum) -> new ReservationView(
            rs.getLong("RESERVATION_ID"),
            ReservationStatus.valueOf(rs.getString("STATUS")),
            time(rs, "STARTS_AT"),
            time(rs, "ENDS_AT"),
            rs.getInt("APPLIED_PRICE"),
            rs.getInt("DEPOSIT"),
            time(rs, "HOLD_EXPIRES_AT"),
            time(rs, "CONFIRMED_AT"),
            rs.getString("CUSTOMER_REQUEST"),
            time(rs, "CREATED_AT"),
            rs.getLong("SHOP_ID"),
            rs.getString("SHOP_NAME"),
            rs.getLong("TREATMENT_ID"),
            rs.getString("TREATMENT_NAME"),
            rs.getLong("USER_ID"),
            rs.getString("CUSTOMER_NAME"),
            rs.getString("CUSTOMER_PHONE"),
            rs.getString("PAYMENT_STATUS"),
            nullableInt(rs, "PAYMENT_AMOUNT"),
            nullableInt(rs, "REFUNDED_AMOUNT"),
            time(rs, "APPROVED_AT"),
            time(rs, "REFUNDED_AT"),
            rs.getString("CANCEL_TYPE"),
            rs.getString("CANCEL_STATUS"),
            nullableInt(rs, "POLICY_REFUND_AMOUNT"),
            nullableInt(rs, "REFUND_AMOUNT"),
            time(rs, "CANCEL_REQUESTED_AT"),
            time(rs, "CANCEL_COMPLETED_AT"));

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
