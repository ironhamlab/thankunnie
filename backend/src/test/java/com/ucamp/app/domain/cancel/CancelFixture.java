package com.ucamp.app.domain.cancel;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 취소 테스트용 FK 데이터. 예약 확정 기능(D)이 없어도 확정 예약을 SQL 로 직접 넣는다.
 */
public final class CancelFixture {

    public static final long CUSTOMER_ID = 1L;
    public static final long OTHER_CUSTOMER_ID = 2L;
    public static final long SHOP_ID = 1L;
    public static final long TREATMENT_ID = 10L;

    private static final String CREATED = "TIMESTAMP '2026-10-22 09:00:00'";

    private CancelFixture() {
    }

    public static void insertBase(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (1, '네일')");
        insertUser(jdbcTemplate, CUSTOMER_ID);
        insertUser(jdbcTemplate, OTHER_CUSTOMER_ID);
        jdbcTemplate.update("""
                INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE, ADDRESS,
                    LATITUDE, LONGITUDE, PHONE, CREATED_AT, UPDATED_AT)
                VALUES (?, 1, 'owner1', 'hash', '매장', '11110', '주소', 37.5, 127.0, '010', %s, %s)
                """.formatted(CREATED, CREATED), SHOP_ID);
        jdbcTemplate.update("""
                INSERT INTO TREATMENT (TREATMENT_ID, SHOP_ID, CATEGORY_ID, NAME, REGULAR_PRICE, PRICE,
                    LAST_MINUTE_PRICE, DURATION_MINUTES, TREATMENT_TYPE, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 1, '젤네일', 25000, 22000, 18000, 60, 'NORMAL', %s, %s)
                """.formatted(CREATED, CREATED), TREATMENT_ID, SHOP_ID);
    }

    public static void insertConfirmedReservation(JdbcTemplate jdbcTemplate, long reservationId, long userId,
            LocalDateTime startsAt) {
        jdbcTemplate.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                    APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CONFIRMED_AT, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, ?, 'CONFIRMED', ?, ?, 22000, 20000, ?, ?, %s, %s)
                """.formatted(CREATED, CREATED), reservationId, userId, TREATMENT_ID,
                Timestamp.valueOf(startsAt), Timestamp.valueOf(startsAt.plusHours(1)),
                Timestamp.valueOf(startsAt.minusDays(3).plusMinutes(5)), Timestamp.valueOf(startsAt.minusDays(3)));
    }

    private static void insertUser(JdbcTemplate jdbcTemplate, long userId) {
        jdbcTemplate.update("""
                INSERT INTO APP_USER (USER_ID, LOGIN_ID, PASSWORD_HASH, NAME, PHONE, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'hash', '고객', '010', %s, %s)
                """.formatted(CREATED, CREATED), userId, "customer" + userId);
    }
}
