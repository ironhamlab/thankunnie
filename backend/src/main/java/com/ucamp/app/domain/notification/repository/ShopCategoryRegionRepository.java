package com.ucamp.app.domain.notification.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 매장의 카테고리와 지역을 읽어 오는 곳.
 * 카테고리·지역 신청과 비교하려고 매장 한 줄만 읽는다. 매장 정보는 여기서 고치지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class ShopCategoryRegionRepository {

    private static final String FIND_BY_SHOP_ID = """
            SELECT CATEGORY_ID, REGION_CODE
            FROM SHOP
            WHERE SHOP_ID = :shopId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 매장 ID로 카테고리 ID와 지역코드를 읽는다.
     *
     * @param shopId 매장 ID
     * @return 매장이 있으면 카테고리와 지역코드. 없거나 ID가 없으면 empty
     */
    /**
     * 이 카테고리·지역에서 영업 중인 매장 ID를 읽는다.
     *
     * @param categoryId 카테고리 ID
     * @param regionCode 지역코드
     * @return 매장 ID. 없거나 조건이 비어 있으면 빈 목록
     */
    public List<Long> findActiveShopIds(Long categoryId, String regionCode) {
        if (categoryId == null || regionCode == null || regionCode.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT SHOP_ID
                FROM SHOP
                WHERE CATEGORY_ID = :categoryId
                  AND REGION_CODE = :regionCode
                  AND STATUS = 'ACTIVE'
                ORDER BY SHOP_ID
                """,
                Map.of("categoryId", categoryId, "regionCode", regionCode),
                (rs, rowNum) -> rs.getLong("SHOP_ID"));
    }

    public Optional<ShopCategoryRegion> findByShopId(Long shopId) {
        if (shopId == null) {
            return Optional.empty();
        }

        // 매장 PK라 없거나 한 건이다. query는 없을 때 예외 대신 빈 목록을 준다.
        List<ShopCategoryRegion> rows = jdbcTemplate.query(
                FIND_BY_SHOP_ID,
                Map.of("shopId", shopId),
                (rs, rowNum) -> toShopCategoryRegion(rs));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    // 조회한 한 행에서 카테고리 ID와 지역코드를 꺼내 결과 객체로 만든다.
    private static ShopCategoryRegion toShopCategoryRegion(ResultSet rs) throws SQLException {
        Long categoryId = rs.getLong("CATEGORY_ID");
        String regionCode = rs.getString("REGION_CODE");
        return new ShopCategoryRegion(categoryId, regionCode);
    }
}
