package com.ucamp.app.domain.cancel.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.cancel.CancelFixture;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig.class)
@ActiveProfiles("test")
class CancelTargetRepositoryTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final LocalDateTime HELD_AT = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Autowired
    private CancelTargetRepository cancelTargetRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        CancelFixture.insertBase(jdbcTemplate);
    }

    @Test
    @DisplayName("예약·매장·결제 완료 정보를 한 번에 읽고, 점유 시각은 예약 생성 시각이다")
    void find_readsReservationShopAndPaidPayment() {
        CancelFixture.insertReservation(jdbcTemplate, 100L, CancelFixture.CUSTOMER_ID, "CONFIRMED", STARTS_AT, HELD_AT);
        CancelFixture.insertPayment(jdbcTemplate, 1L, 100L, "PAID", 20_000, HELD_AT.plusMinutes(2));

        CancelTarget target = cancelTargetRepository.findByReservationId(100L).orElseThrow();

        assertThat(target.reservationId()).isEqualTo(100L);
        assertThat(target.userId()).isEqualTo(CancelFixture.CUSTOMER_ID);
        assertThat(target.shopId()).isEqualTo(CancelFixture.SHOP_ID);
        assertThat(target.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(target.startsAt()).isEqualTo(STARTS_AT);
        assertThat(target.endsAt()).isEqualTo(STARTS_AT.plusHours(1));
        assertThat(target.customerName()).isEqualTo("고객");
        assertThat(target.customerPhone()).isEqualTo(CancelFixture.phoneOf(CancelFixture.CUSTOMER_ID));
        assertThat(target.heldAt()).isEqualTo(HELD_AT);
        assertThat(target.depositPaymentId()).isEqualTo(1L);
        assertThat(target.paidAmount()).isEqualTo(20_000);
        assertThat(target.approvedAt()).isEqualTo(HELD_AT.plusMinutes(2));
        assertThat(target.hasPayment()).isTrue();
        assertThat(target.refundInProgress()).isFalse();
    }

    @Test
    @DisplayName("결제 실패 건은 무시하고 결제 완료 건만 읽는다")
    void find_ignoresNonPaidPayments() {
        CancelFixture.insertReservation(jdbcTemplate, 100L, CancelFixture.CUSTOMER_ID, "CONFIRMED", STARTS_AT, HELD_AT);
        CancelFixture.insertPayment(jdbcTemplate, 1L, 100L, "FAILED", 20_000, null);
        CancelFixture.insertPayment(jdbcTemplate, 2L, 100L, "PAID", 20_000, HELD_AT.plusMinutes(3));

        CancelTarget target = cancelTargetRepository.findByReservationId(100L).orElseThrow();

        assertThat(target.depositPaymentId()).isEqualTo(2L);
        assertThat(target.approvedAt()).isEqualTo(HELD_AT.plusMinutes(3));
    }

    @Test
    @DisplayName("결제가 환불 처리 중이면 결제 완료 건은 없고 환불 진행 중으로 표시된다")
    void find_refundPendingPayment_refundInProgress() {
        CancelFixture.insertReservation(jdbcTemplate, 100L, CancelFixture.CUSTOMER_ID, "CANCEL_PENDING", STARTS_AT,
                HELD_AT);
        CancelFixture.insertPayment(jdbcTemplate, 1L, 100L, "REFUND_PENDING", 20_000, HELD_AT.plusMinutes(2));

        CancelTarget target = cancelTargetRepository.findByReservationId(100L).orElseThrow();

        assertThat(target.hasPayment()).isFalse();
        assertThat(target.refundInProgress()).isTrue();
    }

    @Test
    @DisplayName("한 예약에 결제 완료가 여러 건이면 하나를 고르지 않고 예외로 막는다")
    void find_multiplePaidPayments_throws() {
        CancelFixture.insertReservation(jdbcTemplate, 100L, CancelFixture.CUSTOMER_ID, "CONFIRMED", STARTS_AT, HELD_AT);
        CancelFixture.insertPayment(jdbcTemplate, 1L, 100L, "PAID", 20_000, HELD_AT.plusMinutes(2));
        CancelFixture.insertPayment(jdbcTemplate, 2L, 100L, "PAID", 20_000, HELD_AT.plusMinutes(3));

        assertThatThrownBy(() -> cancelTargetRepository.findByReservationId(100L))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("2건");
    }

    @Test
    @DisplayName("결제 완료 정보가 없는 예약은 결제 값이 비어 있다")
    void find_withoutPayment_hasNoPayment() {
        CancelFixture.insertReservation(jdbcTemplate, 100L, CancelFixture.CUSTOMER_ID, "HOLD", STARTS_AT, HELD_AT);

        CancelTarget target = cancelTargetRepository.findByReservationId(100L).orElseThrow();

        assertThat(target.depositPaymentId()).isNull();
        assertThat(target.paidAmount()).isNull();
        assertThat(target.approvedAt()).isNull();
        assertThat(target.hasPayment()).isFalse();
        assertThat(target.refundInProgress()).isFalse();
    }

    @Test
    @DisplayName("없는 예약은 빈 결과다")
    void find_missingReservation_empty() {
        assertThat(cancelTargetRepository.findByReservationId(999L)).isEmpty();
    }
}
