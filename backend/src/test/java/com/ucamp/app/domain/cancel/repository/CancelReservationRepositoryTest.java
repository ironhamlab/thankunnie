package com.ucamp.app.domain.cancel.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.cancel.CancelFixture;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig.class)
@ActiveProfiles("test")
class CancelReservationRepositoryTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0);

    @Autowired
    private CancelReservationRepository cancelReservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        CancelFixture.insertBase(jdbcTemplate);
        CancelFixture.insertConfirmedReservation(jdbcTemplate, 100L, CancelFixture.CUSTOMER_ID, STARTS_AT);
    }

    @Test
    @DisplayName("현재 상태가 맞으면 상태와 수정 시각을 바꾸고 1을 반환한다")
    void changeStatus_matchingStatus_updates() {
        int changed = cancelReservationRepository.changeStatus(100L, ReservationStatus.CONFIRMED,
                ReservationStatus.CANCEL_PENDING, NOW);

        assertThat(changed).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT STATUS FROM RESERVATION WHERE RESERVATION_ID = 100",
                String.class)).isEqualTo("CANCEL_PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT UPDATED_AT FROM RESERVATION WHERE RESERVATION_ID = 100",
                Timestamp.class).toLocalDateTime()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("현재 상태가 다르면 바꾸지 않고 0을 반환한다 (동시 취소는 한 건만 성공)")
    void changeStatus_otherStatus_noUpdate() {
        cancelReservationRepository.changeStatus(100L, ReservationStatus.CONFIRMED,
                ReservationStatus.CANCEL_PENDING, NOW);

        int changed = cancelReservationRepository.changeStatus(100L, ReservationStatus.CONFIRMED,
                ReservationStatus.CANCEL_PENDING, NOW);

        assertThat(changed).isZero();
        assertThat(cancelReservationRepository.changeStatus(999L, ReservationStatus.CONFIRMED,
                ReservationStatus.CANCEL_PENDING, NOW)).isZero();
    }
}
