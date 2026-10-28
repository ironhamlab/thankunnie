package com.ucamp.app.domain.explore.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ucamp.app.domain.explore.repository.ExploreVacancyCondition.MapBounds;

/**
 * 고객 빈자리 탐색 후보 조회 (C). 매장(A)·시술·시간대 열기(B)는 각 담당 도메인 소유라 엔티티 없이 읽기 전용 SQL 로 본다.
 * 조회 기간과 겹치는 OPEN 시간대 × 그 매장의 판매 중인 시술을 돌려준다.
 */
@Repository
public class ExploreVacancyQueryRepository {

    private static final String ACTIVE = "ACTIVE";
    private static final String OPEN = "OPEN";

    private static final String SELECT_FROM = """
            SELECT e.STARTS_AT AS OPEN_STARTS_AT, e.ENDS_AT AS OPEN_ENDS_AT,
                   s.SHOP_ID, s.NAME AS SHOP_NAME, s.REGION_CODE, s.ADDRESS, s.LATITUDE, s.LONGITUDE,
                   s.IMAGE_URL AS SHOP_IMAGE_URL,
                   t.TREATMENT_ID, t.NAME AS TREATMENT_NAME, t.CATEGORY_ID, c.NAME AS CATEGORY_NAME,
                   t.TREATMENT_TYPE, t.DURATION_MINUTES, t.REGULAR_PRICE, t.PRICE, t.LAST_MINUTE_PRICE,
                   t.IMAGE_URL AS TREATMENT_IMAGE_URL
            FROM SHOP_SCHEDULE_EXCEPTION e
            JOIN SHOP s ON s.SHOP_ID = e.SHOP_ID
            JOIN TREATMENT t ON t.SHOP_ID = s.SHOP_ID
            JOIN CATEGORY c ON c.CATEGORY_ID = t.CATEGORY_ID
            """;

    private static final String ORDER_BY = " ORDER BY e.STARTS_AT, t.TREATMENT_ID";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ExploreVacancyQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ExploreVacancyCandidate> findCandidates(ExploreVacancyCondition condition) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        return jdbcTemplate.query(SELECT_FROM + where(condition, params) + ORDER_BY, params, ROW_MAPPER);
    }

    private static String where(ExploreVacancyCondition condition, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>(List.of(
                "e.EXCEPTION_TYPE = :open",
                "s.STATUS = :active",
                "t.STATUS = :active",
                // 시간대가 조회 기간과 겹치면 후보로 본다 (종료 시각 미포함)
                "e.ENDS_AT > :from",
                "e.STARTS_AT < :to"));
        params.addValue("open", OPEN)
                .addValue("active", ACTIVE)
                .addValue("from", condition.from())
                .addValue("to", condition.to());
        if (!condition.regionCodes().isEmpty()) {
            conditions.add("s.REGION_CODE IN (:regionCodes)");
            params.addValue("regionCodes", condition.regionCodes());
        }
        if (condition.categoryId() != null) {
            conditions.add("t.CATEGORY_ID = :categoryId");
            params.addValue("categoryId", condition.categoryId());
        }
        if (condition.shopId() != null) {
            conditions.add("s.SHOP_ID = :shopId");
            params.addValue("shopId", condition.shopId());
        }
        if (condition.keyword() != null) {
            conditions.add("(UPPER(s.NAME) LIKE :keyword ESCAPE '\\' OR UPPER(t.NAME) LIKE :keyword ESCAPE '\\')");
            params.addValue("keyword", "%" + escapeLike(condition.keyword().toUpperCase()) + "%");
        }
        MapBounds bounds = condition.bounds();
        if (bounds != null) {
            conditions.add("s.LATITUDE BETWEEN :minLat AND :maxLat");
            conditions.add("s.LONGITUDE BETWEEN :minLng AND :maxLng");
            params.addValue("minLat", bounds.minLat())
                    .addValue("maxLat", bounds.maxLat())
                    .addValue("minLng", bounds.minLng())
                    .addValue("maxLng", bounds.maxLng());
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    // 검색어의 % · _ 가 와일드카드로 해석되지 않게 한다
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static final RowMapper<ExploreVacancyCandidate> ROW_MAPPER = (rs, rowNum) -> new ExploreVacancyCandidate(
            time(rs, "OPEN_STARTS_AT"),
            time(rs, "OPEN_ENDS_AT"),
            rs.getLong("SHOP_ID"),
            rs.getString("SHOP_NAME"),
            rs.getString("REGION_CODE"),
            rs.getString("ADDRESS"),
            rs.getBigDecimal("LATITUDE"),
            rs.getBigDecimal("LONGITUDE"),
            rs.getString("SHOP_IMAGE_URL"),
            rs.getLong("TREATMENT_ID"),
            rs.getString("TREATMENT_NAME"),
            rs.getLong("CATEGORY_ID"),
            rs.getString("CATEGORY_NAME"),
            rs.getString("TREATMENT_TYPE"),
            rs.getInt("DURATION_MINUTES"),
            rs.getInt("REGULAR_PRICE"),
            rs.getInt("PRICE"),
            rs.getInt("LAST_MINUTE_PRICE"),
            rs.getString("TREATMENT_IMAGE_URL"));

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }
}
