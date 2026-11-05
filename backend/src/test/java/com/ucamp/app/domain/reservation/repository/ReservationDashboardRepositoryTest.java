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

// 초기 데이터 매장 1(가입 2026-10-01 09:00): 확정 1(고객 1, 11/2 14:00)·5(고객 2, 11/3 18:00),
// 취소 완료 3(고객 1, 11/5 11:00, 18,000 납부·18,000 환불), 만료 6. 매장 2: 확정 2(10/10), 취소 처리 중 4(11/7)
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
    @DisplayName("확정·취소 완료 예약만 시작 시각 순으로 담고, 고객·취소 정보와 남은 예약금을 붙인다")
    void findRows_shop1_confirmedAndCancelledInStartOrder() {
        List<ReservationDashboardRow> rows = reservationDashboardRepository.findRows(1L, NOV_START, DEC_START);

        // 만료 6 은 빠진다
        assertThat(rows).extracting(ReservationDashboardRow::reservationId).containsExactly(1L, 5L, 3L);
        ReservationDashboardRow confirmed = rows.get(0);
        assertThat(confirmed.userId()).isEqualTo(1L);
        assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.treatmentName()).isEqualTo("젤네일 원컬러");
        assertThat(confirmed.endsAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 15, 0));
        assertThat(confirmed.holdExpiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 5));
        assertThat(confirmed.createdAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 10, 0));
        assertThat(confirmed.appliedPrice()).isEqualTo(30000);
        assertThat(confirmed.cancelType()).isNull();
        assertThat(confirmed.retainedDeposit()).isZero();
        assertThat(rows.get(1).userId()).isEqualTo(2L);

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

    @Test
    @DisplayName("이용 추정은 이 매장 확정 예약 중 종료 시각이 기준 이하인 예약이다 (경계 포함)")
    void findVisits_shop1_confirmedEndedUntil() {
        List<ReservationDashboardRepository.Visit> visits =
                reservationDashboardRepository.findVisits(1L, LocalDateTime.of(2026, 11, 2, 15, 0));

        assertThat(visits).extracting(ReservationDashboardRepository.Visit::getUserId).containsExactly(1L);
        assertThat(visits.get(0).getEndsAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 15, 0));
        assertThat(reservationDashboardRepository.findVisits(1L, LocalDateTime.of(2026, 12, 1, 0, 0)))
                .extracting(ReservationDashboardRepository.Visit::getUserId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("서비스 이용 시작은 매장 가입 시각과 첫 확정·취소 완료 예약 시작 시각을 함께 준다")
    void findServiceStart_shop1() {
        ReservationDashboardRepository.ServiceStart start =
                reservationDashboardRepository.findServiceStart(1L).orElseThrow();

        assertThat(start.getShopCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0));
        assertThat(start.getFirstStartsAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 14, 0));
        assertThat(reservationDashboardRepository.findServiceStart(999L)).isEmpty();
    }

    @Test
    @DisplayName("찜 고객 수는 이 매장의 사용 중인 업장 알림 고객만 한 번씩 센다")
    void countFavoriteCustomers_activeStoreSubscriptionsOnly() {
        insertSubscription(100, 1, 1L, "STORE", "ACTIVE");
        // 같은 고객의 다른 희망 시간 알림은 한 명으로 센다
        insertSubscription(101, 1, 1L, "STORE", "ACTIVE");
        insertSubscription(102, 2, 1L, "STORE", "ACTIVE");
        // 해지한 알림, 다른 매장 알림은 세지 않는다
        insertSubscription(103, 21, 1L, "STORE", "INACTIVE");
        insertSubscription(104, 21, 2L, "STORE", "ACTIVE");

        assertThat(reservationDashboardRepository.countFavoriteCustomers(1L)).isEqualTo(2);
        assertThat(reservationDashboardRepository.countFavoriteCustomers(999L)).isZero();
    }

    private void insertSubscription(long id, long userId, Long shopId, String type, String status) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 25, 9, 0);
        entityManager.createNativeQuery("""
                INSERT INTO NOTIFICATION_SUBSCRIPTION (SUBSCRIPTION_ID, USER_ID, SHOP_ID, SUBSCRIPTION_TYPE,
                                                       STATUS, CREATED_AT, UPDATED_AT)
                VALUES (:id, :userId, :shopId, :type, :status, :createdAt, :createdAt)
                """)
                .setParameter("id", id)
                .setParameter("userId", userId)
                .setParameter("shopId", shopId)
                .setParameter("type", type)
                .setParameter("status", status)
                .setParameter("createdAt", createdAt)
                .executeUpdate();
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
