package com.ucamp.app.domain.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

// 초기 데이터: 확정 1(11/2 14:00~15:00), 확정 5(11/3 18:00~19:00), 취소 완료 3(11/5)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class ReservationRepositoryNoShowTest {

    private static final ReservationStatus CONFIRMED = ReservationStatus.CONFIRMED;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("끝난 확정 예약은 한 번만 노쇼로 바뀌고, 해제도 한 번만 된다 (종료 시각 경계 포함)")
    void changeNoShow_endedConfirmed_changesOnce() {
        LocalDateTime endsAt = LocalDateTime.of(2026, 11, 2, 15, 0);

        assertThat(reservationRepository.changeNoShow(1L, false, true, CONFIRMED, endsAt)).isEqualTo(1);
        assertThat(noShowOf(1L)).isEqualTo("Y");
        assertThat(reservationRepository.changeNoShow(1L, false, true, CONFIRMED, endsAt)).isZero();

        assertThat(reservationRepository.changeNoShow(1L, true, false, CONFIRMED, endsAt.plusDays(30))).isEqualTo(1);
        assertThat(noShowOf(1L)).isEqualTo("N");
        Reservation reservation = reservationRepository.findById(1L).orElseThrow();
        assertThat(reservation.isNoShow()).isFalse();
    }

    @Test
    @DisplayName("아직 끝나지 않은 예약, 취소 완료 예약은 바뀌지 않는다")
    void changeNoShow_notEndedOrCancelled_unchanged() {
        assertThat(reservationRepository.changeNoShow(5L, false, true, CONFIRMED,
                LocalDateTime.of(2026, 11, 3, 18, 30))).isZero();
        assertThat(reservationRepository.changeNoShow(3L, false, true, CONFIRMED,
                LocalDateTime.of(2026, 12, 1, 0, 0))).isZero();
        assertThat(noShowOf(5L)).isEqualTo("N");
        assertThat(noShowOf(3L)).isEqualTo("N");
    }

    private String noShowOf(Long reservationId) {
        return jdbcTemplate.queryForObject("SELECT NO_SHOW FROM RESERVATION WHERE RESERVATION_ID = ?",
                String.class, reservationId);
    }
}
