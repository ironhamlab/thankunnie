package com.ucamp.app.domain.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.ucamp.app.common.policy.ReservationStatus;
import com.ucamp.app.config.JpaAuditingConfig;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;

// application-test.yml 의 H2(Oracle 호환 모드)와 DDL 스키마를 그대로 쓴다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@Sql("/sql/reservation-fixture.sql")
class ReservationRepositoryTest {

    private static final EnumSet<ReservationStatus> OCCUPYING_STATUSES = EnumSet.of(
            ReservationStatus.HOLD, ReservationStatus.CONFIRMED, ReservationStatus.CANCEL_PENDING);

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TreatmentRepository treatmentRepository;

    @Test
    @DisplayName("같은 매장에서 시간이 겹치고 시간을 잡는 상태인 예약만 조회한다")
    void findOverlappingInShop_returnsOnlySameShopOverlappingOccupied() {
        List<Reservation> overlapping = reservationRepository.findOverlappingInShop(1L,
                LocalDateTime.of(2026, 11, 1, 14, 30), LocalDateTime.of(2026, 11, 1, 15, 30), OCCUPYING_STATUSES);

        assertThat(overlapping).extracting(Reservation::getId).containsExactly(1001L);
    }

    @Test
    @DisplayName("점유 예약을 시퀀스 ID 와 생성·수정 시각과 함께 저장한다")
    void save_holdReservation_assignsIdAndAuditTimes() {
        Treatment treatment = treatmentRepository.findById(1L).orElseThrow();
        Reservation reservation = Reservation.builder()
                .userId(1L)
                .treatment(treatment)
                .startsAt(LocalDateTime.of(2026, 11, 1, 18, 0))
                .endsAt(LocalDateTime.of(2026, 11, 1, 19, 0))
                .appliedPrice(30_000)
                .deposit(20_000)
                .holdExpiresAt(LocalDateTime.of(2026, 11, 1, 9, 5))
                .build();

        Reservation saved = reservationRepository.saveAndFlush(reservation);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.HOLD);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("매장 잠금은 매장 ID 를 반환한다")
    void lockShop_existingShop_returnsShopId() {
        assertThat(reservationRepository.lockShop(1L)).isEqualTo(1L);
    }
}
