package com.ucamp.app.domain.cancel.service;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.cancel.CancelFixture;
import com.ucamp.app.domain.cancel.dto.response.CancelResultResponse;
import com.ucamp.app.domain.cancel.entity.CancelStatus;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.cancel.entity.ReservationCancel;
import com.ucamp.app.domain.cancel.repository.ReservationCancelRepository;
import com.ucamp.app.domain.notification.event.SlotReopenedEvent;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;
import com.ucamp.app.domain.schedule.entity.ShopScheduleException;
import com.ucamp.app.domain.schedule.repository.ShopScheduleExceptionRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TimeConfig.class, CancelResultService.class, GraceCancelUsageService.class})
@ActiveProfiles("test")
@RecordApplicationEvents
class CancelResultServiceTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0);

    @Autowired
    private CancelResultService cancelResultService;

    @Autowired
    private GraceCancelUsageService graceCancelUsageService;

    @Autowired
    private ReservationCancelRepository reservationCancelRepository;

    @Autowired
    private ShopScheduleExceptionRepository shopScheduleExceptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    private ReservationCancel cancel;

    @BeforeEach
    void setUp() {
        CancelFixture.insertBase(jdbcTemplate);
        CancelFixture.insertReservation(jdbcTemplate, 100L, CancelFixture.CUSTOMER_ID, "CANCEL_PENDING", STARTS_AT,
                NOW.minusMinutes(5));
        // 무료 취소로 정책 10,000원보다 많은 20,000원 환불을 접수한 상태
        cancel = reservationCancelRepository.saveAndFlush(ReservationCancel.builder()
                .reservationId(100L)
                .userId(CancelFixture.CUSTOMER_ID)
                .cancelType(CancelType.CUSTOMER)
                .policyRefundAmount(10_000)
                .refundAmount(20_000)
                .requestedAt(NOW)
                .build());
        graceCancelUsageService.record(CancelFixture.CUSTOMER_ID, NOW.toLocalDate(), cancel.getId());
    }

    @Test
    @DisplayName("결제 취소 성공을 반영하면 취소 완료, 예약 취소 완료가 된다")
    void complete_cancelledAndCompleted() {
        CancelResultResponse response = cancelResultService.complete(cancel.getId(), NOW.plusSeconds(3));

        assertThat(response.cancelStatus()).isEqualTo(CancelStatus.COMPLETED);
        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(response.refundAmount()).isEqualTo(20_000);
        assertThat(response.graceApplied()).isTrue();
        assertThat(response.completedAt()).isNotNull();
        assertThat(reservationStatus()).isEqualTo("CANCELLED");
        assertThat(cancel.getCompletedAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, NOW.toLocalDate())).isTrue();
        assertThat(applicationEvents.stream(SlotReopenedEvent.class))
                .containsExactly(new SlotReopenedEvent(100L));
    }

    @Test
    @DisplayName("사장님이 취소하며 닫은 시간은 취소 완료돼도 재공개 이벤트를 발행하지 않는다")
    void complete_closed_noReopenEvent() {
        saveClose();

        cancelResultService.complete(cancel.getId(), NOW.plusSeconds(3));

        assertThat(reservationStatus()).isEqualTo("CANCELLED");
        assertThat(applicationEvents.stream(SlotReopenedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("결제 취소 실패를 반영하면 취소하며 등록한 닫기도 지운다")
    void fail_deletesCloseSchedule() {
        saveClose();

        cancelResultService.fail(cancel.getId(), NOW.plusSeconds(3));

        assertThat(shopScheduleExceptionRepository.existsByReservationId(100L)).isFalse();
        assertThat(applicationEvents.stream(SlotReopenedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("결제 취소 실패를 반영하면 예약은 확정으로 돌아가고 무료 취소 기록은 지워진다")
    void fail_restoresConfirmedAndReleasesGrace() {
        cancelResultService.fail(cancel.getId(), NOW.plusSeconds(3));

        assertThat(reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(cancel.getStatus()).isEqualTo(CancelStatus.FAILED);
        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, NOW.toLocalDate())).isFalse();
    }

    @Test
    @DisplayName("결제 취소 결과가 불명이면 예약은 취소 처리 중으로 남아 시간을 계속 막는다")
    void markUnknown_keepsCancelPending() {
        CancelResultResponse response = cancelResultService.markUnknown(cancel.getId());

        assertThat(response.cancelStatus()).isEqualTo(CancelStatus.UNKNOWN);
        assertThat(response.reservationStatus()).isEqualTo(ReservationStatus.CANCEL_PENDING);
        assertThat(response.completedAt()).isNull();
        assertThat(reservationStatus()).isEqualTo("CANCEL_PENDING");
        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, NOW.toLocalDate())).isTrue();
    }

    @Test
    @DisplayName("예약이 취소 처리 중이 아니면 결과를 반영하지 않고 예외가 발생한다")
    void complete_reservationNotPending_throws() {
        jdbcTemplate.update("UPDATE RESERVATION SET STATUS = 'CONFIRMED' WHERE RESERVATION_ID = 100");

        assertThatThrownBy(() -> cancelResultService.complete(cancel.getId(), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private void saveClose() {
        shopScheduleExceptionRepository.save(ShopScheduleException.builder()
                .shopId(CancelFixture.SHOP_ID)
                .reservationId(100L)
                .exceptionType(ScheduleExceptionType.CLOSE)
                .startsAt(STARTS_AT)
                .endsAt(STARTS_AT.plusHours(1))
                .reason("원장님 부상")
                .build());
    }

    private String reservationStatus() {
        return jdbcTemplate.queryForObject("SELECT STATUS FROM RESERVATION WHERE RESERVATION_ID = 100", String.class);
    }
}
