package com.ucamp.app.domain.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.ShopLock;
import com.ucamp.app.config.JpaAuditingConfig;
import com.ucamp.app.domain.reservation.entity.Reservation;

// 스키마는 docs/erd/01_schema_ddl.sql 로 만든 test 프로필 H2(MODE=Oracle)를 그대로 쓴다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, ShopLock.class})
@ActiveProfiles("test")
class ReservationRepositoryTest {

    private static final long SHOP_ID = 1L;
    private static final long OTHER_SHOP_ID = 2L;
    private static final long TREATMENT_ID = 10L;
    private static final long OTHER_TREATMENT_ID = 20L;
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 25, 12, 0);

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ShopLock shopLock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long nextReservationId = 100L;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (1, '네일')");
        jdbcTemplate.update("""
                INSERT INTO USERS (USER_ID, LOGIN_ID, PASSWORD_HASH, NAME, PHONE, CREATED_AT, UPDATED_AT)
                VALUES (1, 'customer1', 'hash', '고객', '010', SYSTIMESTAMP, SYSTIMESTAMP)
                """);
        insertShopWithTreatment(SHOP_ID, TREATMENT_ID);
        insertShopWithTreatment(OTHER_SHOP_ID, OTHER_TREATMENT_ID);
    }

    @Test
    @DisplayName("매장의 구간과 겹치고 취소·만료가 아닌 예약만 시작 시각 순으로 조회하며 종료 시각은 포함하지 않는다")
    void findActiveOverlapping_returnsActiveOverlappingInShop() {
        long hold = insertReservation(TREATMENT_ID, "HOLD", NOON.plusMinutes(30), NOON.plusMinutes(90));
        long confirmed = insertReservation(TREATMENT_ID, "CONFIRMED", NOON, NOON.plusMinutes(30));
        long cancelPending = insertReservation(TREATMENT_ID, "CANCEL_PENDING", NOON.plusHours(1), NOON.plusHours(2));
        insertReservation(TREATMENT_ID, "CANCELLED", NOON, NOON.plusHours(1));
        insertReservation(TREATMENT_ID, "EXPIRED", NOON, NOON.plusHours(1));
        insertReservation(TREATMENT_ID, "CONFIRMED", NOON.plusHours(2), NOON.plusHours(3));
        insertReservation(OTHER_TREATMENT_ID, "CONFIRMED", NOON, NOON.plusHours(1));

        List<Reservation> result = reservationRepository.findActiveOverlapping(SHOP_ID, NOON, NOON.plusHours(2));

        assertThat(result).extracting(Reservation::getId).containsExactly(confirmed, hold, cancelPending);
    }

    @Test
    @DisplayName("빌더로 만든 예약은 HOLD 상태로 시퀀스 ID 를 받아 저장된다")
    void save_newReservation_isHold() {
        Reservation saved = reservationRepository.saveAndFlush(Reservation.builder()
                .userId(1L)
                .treatmentId(TREATMENT_ID)
                .startsAt(NOON)
                .endsAt(NOON.plusHours(1))
                .appliedPrice(22_000)
                .deposit(20_000)
                .holdExpiresAt(NOON.minusHours(3))
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus().name()).isEqualTo("HOLD");
    }

    @Test
    @DisplayName("매장 잠금은 있는 매장이면 통과하고 없는 매장이면 NOT_FOUND 예외가 발생한다")
    void shopLock_locksExistingShopOnly() {
        assertThatCode(() -> shopLock.lock(SHOP_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> shopLock.lock(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private void insertShopWithTreatment(long shopId, long treatmentId) {
        jdbcTemplate.update("""
                INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE, ADDRESS,
                    LATITUDE, LONGITUDE, PHONE, CREATED_AT, UPDATED_AT)
                VALUES (?, 1, ?, 'hash', '매장', 'SEOUL', '주소', 37.5, 127.0, '010', SYSTIMESTAMP, SYSTIMESTAMP)
                """, shopId, "owner" + shopId);
        jdbcTemplate.update("""
                INSERT INTO TREATMENT (TREATMENT_ID, SHOP_ID, NAME, REGULAR_PRICE, PRICE, LAST_MINUTE_PRICE,
                    DURATION_MINUTES, TREATMENT_TYPE, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, '젤네일', 25000, 22000, 18000, 60, 'NORMAL', SYSTIMESTAMP, SYSTIMESTAMP)
                """, treatmentId, shopId);
    }

    private long insertReservation(long treatmentId, String status, LocalDateTime startsAt, LocalDateTime endsAt) {
        long id = nextReservationId++;
        jdbcTemplate.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                    APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (?, 1, ?, ?, ?, ?, 22000, 20000, ?, SYSTIMESTAMP, SYSTIMESTAMP)
                """, id, treatmentId, status, Timestamp.valueOf(startsAt), Timestamp.valueOf(endsAt),
                Timestamp.valueOf(startsAt.minusHours(3)));
        return id;
    }
}
