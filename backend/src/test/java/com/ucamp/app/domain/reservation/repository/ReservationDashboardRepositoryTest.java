package com.ucamp.app.domain.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

// 초기 데이터 11월 매장 1: 확정 1(11/2 14:00, 30,000)·5(11/3 18:00, 30,000), 취소 완료 3(11/5 11:00, 10/22 10:00 완료), 만료 6
// 매장 2: 취소 처리 중 4(11/7). 재판매 건은 테스트마다 추가한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReservationDashboardRepository.class, TimeConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class ReservationDashboardRepositoryTest {

    private static final LocalDateTime NOV_START = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDateTime DEC_START = LocalDateTime.of(2026, 12, 1, 0, 0);
    private static final LocalDateTime NOV_2 = LocalDateTime.of(2026, 11, 2, 0, 0);

    @Autowired
    private ReservationDashboardRepository reservationDashboardRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("이번 달 확정 건수·예상 매출과 오늘 확정·남은 확정 건수를 센다")
    void countReservations_shop1_countsConfirmedAndToday() {
        ReservationDashboardCounts beforeStart = reservationDashboardRepository.countReservations(
                1L, NOV_START, DEC_START, NOV_2, NOV_2.withHour(13));
        ReservationDashboardCounts afterStart = reservationDashboardRepository.countReservations(
                1L, NOV_START, DEC_START, NOV_2, NOV_2.withHour(14));

        assertThat(beforeStart.confirmedCount()).isEqualTo(2);
        assertThat(beforeStart.expectedSales()).isEqualTo(60000);
        assertThat(beforeStart.cancelPendingCount()).isZero();
        assertThat(beforeStart.todayConfirmedCount()).isEqualTo(1);
        assertThat(beforeStart.todayUpcomingCount()).isEqualTo(1);
        // 시작 시각이 되면 남은 예약에서 빠지고 오늘 확정 건수는 그대로다
        assertThat(afterStart.todayConfirmedCount()).isEqualTo(1);
        assertThat(afterStart.todayUpcomingCount()).isZero();
    }

    @Test
    @DisplayName("다른 매장 예약과 기간 밖 예약은 세지 않는다")
    void countReservations_scopeAndPeriod_applied() {
        ReservationDashboardCounts shop2 = reservationDashboardRepository.countReservations(
                2L, NOV_START, DEC_START, NOV_2, NOV_2);
        ReservationDashboardCounts october = reservationDashboardRepository.countReservations(
                1L, LocalDateTime.of(2026, 10, 1, 0, 0), NOV_START, LocalDateTime.of(2026, 10, 29, 0, 0),
                LocalDateTime.of(2026, 10, 29, 12, 0));

        assertThat(shop2.confirmedCount()).isZero();
        assertThat(shop2.cancelPendingCount()).isEqualTo(1);
        assertThat(october.confirmedCount()).isZero();
        assertThat(october.expectedSales()).isZero();
    }

    @Test
    @DisplayName("취소 완료 건을 유형별로 세고, 취소 처리 중 건은 빼며, 없는 유형은 0 이다")
    void countCancelledByType_countsCompletedOnly() {
        Map<CancelType, Integer> shop1 = reservationDashboardRepository.countCancelledByType(1L, NOV_START, DEC_START);
        Map<CancelType, Integer> shop2 = reservationDashboardRepository.countCancelledByType(2L, NOV_START, DEC_START);

        assertThat(shop1).containsEntry(CancelType.CUSTOMER, 1)
                .containsEntry(CancelType.SHOP_FAULT, 0)
                .containsEntry(CancelType.CUSTOMER_REQUEST, 0);
        assertThat(shop2.values()).allMatch(count -> count == 0);
    }

    @Test
    @DisplayName("취소 완료 후 같은 시간에 들어온 확정 예약만 재판매로 센다")
    void countRebooked_countsConfirmedAfterCancelCompleted() {
        // 취소 완료(10/22 10:00) 뒤 생성, 시간 겹침 → 재판매
        insertReservation(7L, 2L, "CONFIRMED", "2026-11-05 11:00:00", "2026-11-05 11:30:00", 15000,
                "2026-10-23 09:00:00");
        // 취소 완료 전에 생성 → 재판매 아님
        insertReservation(8L, 2L, "CONFIRMED", "2026-11-05 11:00:00", "2026-11-05 11:30:00", 18000,
                "2026-10-22 09:00:00");
        // 취소 완료 뒤 생성이지만 시간이 겹치지 않음 (끝 시각 = 다음 시작 시각) → 재판매 아님
        insertReservation(9L, 2L, "CONFIRMED", "2026-11-05 11:30:00", "2026-11-05 12:00:00", 18000,
                "2026-10-23 09:00:00");
        // 겹치지만 만료 → 재판매 아님
        insertReservation(10L, 2L, "EXPIRED", "2026-11-05 11:00:00", "2026-11-05 11:30:00", 18000,
                "2026-10-23 10:00:00");

        ReservationRebookStats shop1 = reservationDashboardRepository.countRebooked(1L, NOV_START, DEC_START);
        ReservationRebookStats shop2 = reservationDashboardRepository.countRebooked(2L, NOV_START, DEC_START);

        assertThat(shop1.count()).isEqualTo(1);
        assertThat(shop1.sales()).isEqualTo(15000);
        assertThat(shop2.count()).isZero();
        assertThat(shop2.sales()).isZero();
    }

    @Test
    @DisplayName("통계 행은 확정·취소 완료만 시작 시각 순으로 담고 재판매 여부를 표시한다")
    void findStatsRows_confirmedAndCancelled_withRebookFlag() {
        insertReservation(7L, 2L, "CONFIRMED", "2026-11-05 11:00:00", "2026-11-05 11:30:00", 15000,
                "2026-10-23 09:00:00");

        List<ReservationStatsRow> rows = reservationDashboardRepository.findStatsRows(1L, NOV_START, DEC_START);

        // 만료 6 은 빠진다. 같은 시작 시각이면 예약 ID 순 (취소 3 → 재판매 7)
        assertThat(rows).extracting(ReservationStatsRow::status).containsExactly(
                ReservationStatus.CONFIRMED, ReservationStatus.CONFIRMED,
                ReservationStatus.CANCELLED, ReservationStatus.CONFIRMED);
        assertThat(rows).extracting(ReservationStatsRow::rebooked).containsExactly(false, false, false, true);
        assertThat(rows.get(0).treatmentName()).isEqualTo("젤네일 원컬러");
        assertThat(rows.get(0).holdExpiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 5));
        assertThat(rows.get(3).appliedPrice()).isEqualTo(15000);
    }

    private void insertReservation(Long id, Long treatmentId, String status, String startsAt, String endsAt,
            int appliedPrice, String createdAt) {
        jdbcTemplate.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                                         APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (?, 2, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, treatmentId, status, LocalDateTime.parse(startsAt.replace(' ', 'T')),
                LocalDateTime.parse(endsAt.replace(' ', 'T')), appliedPrice, Math.min(appliedPrice, 20000),
                LocalDateTime.parse(createdAt.replace(' ', 'T')).plusMinutes(5),
                LocalDateTime.parse(createdAt.replace(' ', 'T')), LocalDateTime.parse(createdAt.replace(' ', 'T')));
    }
}
