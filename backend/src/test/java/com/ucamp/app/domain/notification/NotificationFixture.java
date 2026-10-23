package com.ucamp.app.domain.notification;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 알림 테스트용 다른 도메인 테이블 데이터. 해당 엔티티가 없으므로 SQL 로 직접 넣는다.
 */
public final class NotificationFixture {

    private NotificationFixture() {
    }

    public static void category(JdbcTemplate jdbc, long categoryId, String name) {
        jdbc.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (?, ?)", categoryId, name);
    }

    public static void shop(JdbcTemplate jdbc, long shopId, long categoryId, String regionCode, String status) {
        jdbc.update("""
                INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE, ADDRESS,
                                  LATITUDE, LONGITUDE, PHONE, STATUS, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, ?, 'hash', ?, ?, '주소', 37.5, 127.0, '010-0000-0000', ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, shopId, categoryId, "shop" + shopId, "매장" + shopId, regionCode, status);
    }

    public static void user(JdbcTemplate jdbc, long userId) {
        jdbc.update("""
                INSERT INTO USERS (USER_ID, LOGIN_ID, PASSWORD_HASH, NAME, PHONE, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'hash', '회원', '010-1111-1111', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId, "user" + userId);
    }

    public static void treatment(JdbcTemplate jdbc, long treatmentId, long shopId, String treatmentType) {
        jdbc.update("""
                INSERT INTO TREATMENT (TREATMENT_ID, SHOP_ID, NAME, REGULAR_PRICE, PRICE, LAST_MINUTE_PRICE,
                                       DURATION_MINUTES, TREATMENT_TYPE, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, '시술', 50000, 40000, 30000, 60, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, treatmentId, shopId, treatmentType);
    }

    public static void exception(JdbcTemplate jdbc, long exceptionId, long shopId, Long reservationId,
            String exceptionType, LocalDateTime startsAt, LocalDateTime endsAt) {
        jdbc.update("""
                INSERT INTO SHOP_SCHEDULE_EXCEPTION (EXCEPTION_ID, SHOP_ID, RESERVATION_ID, EXCEPTION_TYPE,
                                                     STARTS_AT, ENDS_AT, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, exceptionId, shopId, reservationId, exceptionType, startsAt, endsAt);
    }

    public static void reservation(JdbcTemplate jdbc, long reservationId, long userId, long treatmentId,
            String status, LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime holdExpiresAt) {
        jdbc.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                                         APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, ?, ?, ?, ?, 40000, 20000, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, reservationId, userId, treatmentId, status, startsAt, endsAt, holdExpiresAt);
    }
}
