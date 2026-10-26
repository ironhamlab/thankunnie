package com.ucamp.app.domain.reservation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.domain.notification.NotificationFixture;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ReservationHoldExpireSchedulerTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 10, 23, 14, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.shop(jdbcTemplate, 1L, 10L, "SEOUL_GANGNAM", "ACTIVE");
        NotificationFixture.user(jdbcTemplate, 1L);
        NotificationFixture.treatment(jdbcTemplate, 100L, 1L, "NORMAL");
        NotificationFixture.reservation(jdbcTemplate, 500L, 1L, 100L, "HOLD",
                STARTS_AT, STARTS_AT.plusHours(1), STARTS_AT.minusMinutes(10));
    }

    @Test
    @DisplayName("만료 호출 자리는 예약 상태를 바꾸지 않는다")
    void expireHolds_doesNotChangeReservation() {
        new ReservationHoldExpireScheduler().expireHolds();

        String status = jdbcTemplate.queryForObject(
                "SELECT STATUS FROM RESERVATION WHERE RESERVATION_ID = 500", String.class);
        assertThat(status).isEqualTo("HOLD");
    }
}
