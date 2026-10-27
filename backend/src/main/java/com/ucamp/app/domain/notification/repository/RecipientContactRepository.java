package com.ucamp.app.domain.notification.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 문자 받을 회원 전화번호와 매장 이름을 읽는다.
 * 회원·매장 엔티티는 다른 담당 영역이라 SQL 로 읽기만 한다.
 */
@Repository
@RequiredArgsConstructor
public class RecipientContactRepository {

    private static final String FIND_PHONE_BY_USER_ID = """
            SELECT PHONE
            FROM APP_USER
            WHERE USER_ID = :userId
            """;

    private static final String FIND_SHOP_NAME_BY_ID = """
            SELECT NAME
            FROM SHOP
            WHERE SHOP_ID = :shopId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 회원 전화번호를 읽는다.
     *
     * @param userId 회원 ID
     * @return 있으면 전화번호. 없거나 ID가 없으면 empty
     */
    public Optional<String> findPhoneByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        List<String> rows = jdbcTemplate.query(
                FIND_PHONE_BY_USER_ID,
                Map.of("userId", userId),
                (rs, rowNum) -> rs.getString("PHONE"));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(0));
    }

    /**
     * 매장 이름을 읽는다. 문자 본문에 넣기 위해 쓴다.
     *
     * @param shopId 매장 ID
     * @return 있으면 매장 이름. 없거나 ID가 없으면 empty
     */
    public Optional<String> findShopNameById(Long shopId) {
        if (shopId == null) {
            return Optional.empty();
        }
        List<String> rows = jdbcTemplate.query(
                FIND_SHOP_NAME_BY_ID,
                Map.of("shopId", shopId),
                (rs, rowNum) -> rs.getString("NAME"));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(0));
    }
}
