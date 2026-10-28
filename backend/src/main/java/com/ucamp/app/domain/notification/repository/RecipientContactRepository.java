package com.ucamp.app.domain.notification.repository;

import java.time.LocalDateTime;
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

    private static final String FIND_CANCEL_SMS_INFO = """
            SELECT u.PHONE, s.NAME AS SHOP_NAME, r.STARTS_AT
            FROM RESERVATION r
            JOIN APP_USER u ON u.USER_ID = r.USER_ID
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            JOIN SHOP s ON s.SHOP_ID = t.SHOP_ID
            WHERE r.RESERVATION_ID = :reservationId
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

    /**
     * 취소 문자에 넣을 고객 전화번호·매장 이름·예약 시작 시각을 읽는다.
     *
     * @param reservationId 취소된 예약 ID
     * @return 예약이 있으면 정보. 없거나 ID가 없으면 empty
     */
    public Optional<CancelSmsInfo> findCancelSmsInfo(Long reservationId) {
        return findReservationSmsInfo(reservationId);
    }

    /**
     * 예약 확정 문자에 넣을 고객 전화번호·매장 이름·예약 시작 시각을 읽는다.
     * 취소 문자와 같은 값을 쓴다.
     *
     * @param reservationId 확정된 예약 ID
     * @return 예약이 있으면 정보. 없거나 ID가 없으면 empty
     */
    public Optional<CancelSmsInfo> findReservationSmsInfo(Long reservationId) {
        if (reservationId == null) {
            return Optional.empty();
        }
        List<CancelSmsInfo> rows = jdbcTemplate.query(
                FIND_CANCEL_SMS_INFO,
                Map.of("reservationId", reservationId),
                (rs, rowNum) -> new CancelSmsInfo(
                        rs.getString("PHONE"),
                        rs.getString("SHOP_NAME"),
                        rs.getTimestamp("STARTS_AT").toLocalDateTime()));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    /**
     * 취소 문자에 필요한 값.
     *
     * @param phone    고객 전화번호. 없으면 null
     * @param shopName 매장 이름
     * @param startsAt 예약 시작 시각
     */
    public record CancelSmsInfo(String phone, String shopName, LocalDateTime startsAt) {
    }
}
