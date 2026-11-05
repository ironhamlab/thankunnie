package com.ucamp.app.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.user.dto.request.UserWithdrawRequest;

// 진행 중 예약 판단과 알림 일괄 정리 쿼리를 실제 DB 로 확인한다
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
@Transactional
class UserWithdrawalServiceTest {

    private static final long USER_ID = 9900L;
    // 테스트 전용 더미 비밀번호
    private static final String PASSWORD = "CHANGE_ME-1234";
    private static final AuthUser LOGIN_USER = new AuthUser(USER_ID, UserRole.CUSTOMER, null);

    @Autowired
    private UserWithdrawalService userWithdrawalService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private Clock clock;

    private long treatmentId;
    private long shopId;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        treatmentId = jdbc.queryForObject("SELECT MIN(TREATMENT_ID) FROM TREATMENT", Long.class);
        shopId = jdbc.queryForObject("SELECT SHOP_ID FROM TREATMENT WHERE TREATMENT_ID = ?", Long.class,
                treatmentId);
        jdbc.update("""
                INSERT INTO APP_USER (USER_ID, LOGIN_ID, PASSWORD_HASH, NAME, PHONE, STATUS, ROLE,
                                      CREATED_AT, UPDATED_AT)
                VALUES (?, 'leaver', ?, '김탈퇴', '010-9900-9900', 'ACTIVE', 'USER', ?, ?)
                """, USER_ID, passwordEncoder.encode(PASSWORD), ts(now), ts(now));
    }

    @Test
    @DisplayName("탈퇴하면 상태를 바꾸고 이름을 가리며, 알림 해제·받은 알림 삭제·대기 문자를 취소한다")
    void withdraw_cleansUp() {
        // 지난 예약과 만료된 결제 대기는 탈퇴를 막지 않는다
        insertReservation(9901L, "HOLD", now.plusDays(1), now.minusMinutes(1));
        insertReservation(9902L, "CONFIRMED", now.minusDays(1));
        jdbc.update("""
                INSERT INTO NOTIFICATION_SUBSCRIPTION (SUBSCRIPTION_ID, USER_ID, SHOP_ID, SUBSCRIPTION_TYPE, STATUS,
                                                       CREATED_AT, UPDATED_AT)
                VALUES (9901, ?, ?, 'STORE', 'ACTIVE', ?, ?)
                """, USER_ID, shopId, ts(now), ts(now));
        jdbc.update("""
                INSERT INTO NOTIFICATION_INBOX (INBOX_ID, USER_ID, CATEGORY, TITLE, BODY, CREATED_AT, UPDATED_AT)
                VALUES (9901, ?, 'INTEREST', '빈자리', '빈자리가 났어요', ?, ?)
                """, USER_ID, ts(now), ts(now));
        jdbc.update("""
                INSERT INTO NOTIFICATION_EVENT (EVENT_ID, EVENT_TYPE, SHOP_ID, SLOT_STARTS_AT, SLOT_ENDS_AT, STATUS,
                                                CREATED_AT, UPDATED_AT)
                VALUES (9901, 'IMMINENT', ?, ?, ?, 'PENDING', ?, ?)
                """, shopId, ts(now.plusHours(2)), ts(now.plusHours(3)), ts(now), ts(now));
        jdbc.update("""
                INSERT INTO NOTIFICATION_DISPATCH (DISPATCH_ID, EVENT_ID, USER_ID, STATUS, CREATED_AT, UPDATED_AT)
                VALUES (9901, 9901, ?, 'PENDING', ?, ?)
                """, USER_ID, ts(now), ts(now));

        userWithdrawalService.withdraw(LOGIN_USER, new UserWithdrawRequest(PASSWORD));

        assertThat(jdbc.queryForMap("SELECT STATUS, NAME, PHONE FROM APP_USER WHERE USER_ID = ?", USER_ID))
                .containsEntry("STATUS", "INACTIVE")
                .containsEntry("NAME", "탈퇴회원")
                .containsEntry("PHONE", "010-9900-9900");
        assertThat(statusOf("NOTIFICATION_SUBSCRIPTION", "SUBSCRIPTION_ID", 9901L)).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM NOTIFICATION_INBOX WHERE USER_ID = ?", Integer.class,
                USER_ID)).isZero();
        assertThat(statusOf("NOTIFICATION_DISPATCH", "DISPATCH_ID", 9901L)).isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName("방문 전 예약이 있으면 탈퇴할 수 없다")
    void withdraw_upcomingReservation_conflict() {
        insertReservation(9901L, "CONFIRMED", now.plusDays(1));

        assertWithdrawFails(PASSWORD, ErrorCode.USER_WITHDRAW_HAS_RESERVATION);
    }

    @Test
    @DisplayName("만료 전 결제 대기가 있으면 탈퇴할 수 없다")
    void withdraw_activeHold_conflict() {
        insertReservation(9901L, "HOLD", now.plusDays(1));

        assertWithdrawFails(PASSWORD, ErrorCode.USER_WITHDRAW_HAS_RESERVATION);
    }

    @Test
    @DisplayName("환불이 끝나지 않은 취소가 있으면 탈퇴할 수 없다")
    void withdraw_cancelPending_conflict() {
        insertReservation(9901L, "CANCEL_PENDING", now.minusDays(1));

        assertWithdrawFails(PASSWORD, ErrorCode.USER_WITHDRAW_HAS_RESERVATION);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 탈퇴하지 않는다")
    void withdraw_wrongPassword_rejected() {
        assertWithdrawFails("wrong-password", ErrorCode.USER_WITHDRAW_PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("관리자는 탈퇴할 수 없다")
    void withdraw_admin_forbidden() {
        assertThatThrownBy(() -> userWithdrawalService.withdraw(TestUser.ADMIN.toAuthUser(),
                new UserWithdrawRequest(PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private void assertWithdrawFails(String password, ErrorCode errorCode) {
        assertThatThrownBy(() -> userWithdrawalService.withdraw(LOGIN_USER, new UserWithdrawRequest(password)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
        assertThat(statusOf("APP_USER", "USER_ID", USER_ID)).isEqualTo("ACTIVE");
    }

    private void insertReservation(long reservationId, String status, LocalDateTime startsAt) {
        insertReservation(reservationId, status, startsAt, now.plusMinutes(5));
    }

    private void insertReservation(long reservationId, String status, LocalDateTime startsAt,
            LocalDateTime holdExpiresAt) {
        jdbc.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                                         APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, ?, ?, ?, ?, 30000, 20000, ?, ?, ?)
                """, reservationId, USER_ID, treatmentId, status, ts(startsAt.withNano(0)),
                ts(startsAt.withNano(0).plusHours(1)), ts(holdExpiresAt), ts(now), ts(now));
    }

    private String statusOf(String table, String idColumn, long id) {
        return jdbc.queryForObject("SELECT STATUS FROM " + table + " WHERE " + idColumn + " = ?", String.class,
                id);
    }

    private static Timestamp ts(LocalDateTime time) {
        return Timestamp.valueOf(time);
    }
}
