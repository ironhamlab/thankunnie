package com.ucamp.app.domain.vacancy.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 가용 시간 조회 전에 매장이 운영 중인지 확인한다. 매장은 A 담당 도메인이라 엔티티 없이 읽기 전용 SQL 로 본다.
 */
@Repository
public class ShopStatusQueryRepository {

    private static final String ACTIVE = "ACTIVE";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ShopStatusQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isActive(Long shopId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("shopId", shopId)
                .addValue("status", ACTIVE);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SHOP WHERE SHOP_ID = :shopId AND STATUS = :status", params, Long.class);
        return count != null && count > 0;
    }
}
