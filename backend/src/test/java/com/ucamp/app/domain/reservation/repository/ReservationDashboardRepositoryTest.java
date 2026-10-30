package com.ucamp.app.domain.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

// 초기 데이터 매장 1: 확정 1(11/2 14:00)·5(11/3 18:00), 취소 완료 3(11/5 11:00, 18,000 납부·18,000 환불), 만료 6
// 매장 2: 취소 처리 중 4(11/7)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class ReservationDashboardRepositoryTest {

    private static final LocalDateTime NOV_START = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDateTime DEC_START = LocalDateTime.of(2026, 12, 1, 0, 0);

    @Autowired
    private ReservationDashboardRepository reservationDashboardRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("확정·취소 완료 예약만 시작 시각 순으로 담고, 취소 정보와 남은 예약금을 붙인다")
    void findRows_shop1_confirmedAndCancelledInStartOrder() {
        List<ReservationDashboardRow> rows = reservationDashboardRepository.findRows(1L, NOV_START, DEC_START);

        // 만료 6 은 빠진다
        assertThat(rows).extracting(ReservationDashboardRow::reservationId).containsExactly(1L, 5L, 3L);
        ReservationDashboardRow confirmed = rows.get(0);
        assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.treatmentName()).isEqualTo("젤네일 원컬러");
        assertThat(confirmed.endsAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 15, 0));
        assertThat(confirmed.holdExpiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 5));
        assertThat(confirmed.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 0));
        assertThat(confirmed.appliedPrice()).isEqualTo(30000);
        assertThat(confirmed.cancelType()).isNull();
        assertThat(confirmed.retainedDeposit()).isZero();

        ReservationDashboardRow cancelled = rows.get(2);
        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.cancelType()).isEqualTo(CancelType.CUSTOMER);
        assertThat(cancelled.cancelCompletedAt()).isEqualTo(LocalDateTime.of(2026, 10, 22, 10, 0));
        // 전액 환불이라 남은 예약금 0
        assertThat(cancelled.retainedDeposit()).isZero();
        assertThat(cancelled.closedAfterCancel()).isFalse();
    }

    @Test
    @DisplayName("일부 환불 취소는 납부 예약금 - 환불액이 남고, 취소하며 닫은 시간이면 표시한다")
    void findRows_partialRefundAndClosed_retainedAndFlagged() {
        insertCancelledWithClose();

        ReservationDashboardRow row = reservationDashboardRepository.findRows(1L, NOV_START, DEC_START).stream()
                .filter(candidate -> candidate.reservationId() == 7L)
                .findFirst().orElseThrow();

        assertThat(row.cancelType()).isEqualTo(CancelType.CUSTOMER_REQUEST);
        assertThat(row.retainedDeposit()).isEqualTo(15000);
        assertThat(row.closedAfterCancel()).isTrue();
    }

    @Test
    @DisplayName("다른 매장, 취소 처리 중, 조회 구간 밖 예약은 담지 않는다 (구간은 시작 포함·끝 제외)")
    void findRows_scopeStatusAndRange_applied() {
        assertThat(reservationDashboardRepository.findRows(2L, NOV_START, DEC_START)).isEmpty();
        assertThat(reservationDashboardRepository.findRows(1L,
                LocalDateTime.of(2026, 11, 2, 14, 0), LocalDateTime.of(2026, 11, 3, 18, 0)))
                .extracting(ReservationDashboardRow::reservationId).containsExactly(1L);
    }

    private void insertCancelledWithClose() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 25, 9, 0);
        LocalDateTime completedAt = LocalDateTime.of(2026, 11, 8, 9, 0);
        entityManager.createNativeQuery("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                                         APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (7, 2, 1, 'CANCELLED', :startsAt, :endsAt, 30000, 20000, :holdExpiresAt, :createdAt, :completedAt)
                """)
                .setParameter("startsAt", LocalDateTime.of(2026, 11, 9, 14, 0))
                .setParameter("endsAt", LocalDateTime.of(2026, 11, 9, 15, 0))
                .setParameter("holdExpiresAt", createdAt.plusMinutes(5))
                .setParameter("createdAt", createdAt)
                .setParameter("completedAt", completedAt)
                .executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO DEPOSIT_PAYMENT (DEPOSIT_PAYMENT_ID, RESERVATION_ID, PG_PROVIDER, STATUS, AMOUNT,
                                             REFUNDED_AMOUNT, CREATED_AT, UPDATED_AT)
                VALUES (100, 7, 'PORTONE', 'PART_REFUNDED', 20000, 5000, :createdAt, :completedAt)
                """)
                .setParameter("createdAt", createdAt)
                .setParameter("completedAt", completedAt)
                .executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO RESERVATION_CANCEL (CANCEL_ID, RESERVATION_ID, USER_ID, CANCEL_TYPE, POLICY_REFUND_AMOUNT,
                                                REFUND_AMOUNT, STATUS, REQUESTED_AT, COMPLETED_AT, CREATED_AT, UPDATED_AT)
                VALUES (100, 7, 2, 'CUSTOMER_REQUEST', 0, 5000, 'COMPLETED', :completedAt, :completedAt,
                        :completedAt, :completedAt)
                """)
                .setParameter("completedAt", completedAt)
                .executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO SHOP_SCHEDULE_EXCEPTION (EXCEPTION_ID, SHOP_ID, RESERVATION_ID, EXCEPTION_TYPE,
                                                     STARTS_AT, ENDS_AT, CREATED_AT, UPDATED_AT)
                VALUES (100, 1, 7, 'CLOSE', :startsAt, :endsAt, :completedAt, :completedAt)
                """)
                .setParameter("startsAt", LocalDateTime.of(2026, 11, 9, 14, 0))
                .setParameter("endsAt", LocalDateTime.of(2026, 11, 9, 15, 0))
                .setParameter("completedAt", completedAt)
                .executeUpdate();
    }
}
