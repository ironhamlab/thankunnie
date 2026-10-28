package com.ucamp.app.domain.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

class ReservationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 9, 0);
    private static final int DEPOSIT = 20_000;

    @Test
    @DisplayName("만료 전 점유를 예약금과 같은 금액으로 확정하면 CONFIRMED 와 확정 시각이 기록된다")
    void confirm_validHold_confirms() {
        Reservation reservation = hold(NOW.plusMinutes(5));

        reservation.confirm(DEPOSIT, NOW, null);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이미 확정된 예약을 다시 확정하면 RESERVATION_ALREADY_CONFIRMED 예외가 발생한다")
    void confirm_alreadyConfirmed_throws() {
        Reservation reservation = hold(NOW.plusMinutes(5));
        reservation.confirm(DEPOSIT, NOW, null);

        assertErrorCode(reservation, DEPOSIT, NOW.plusMinutes(1), ErrorCode.RESERVATION_ALREADY_CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("만료 처리 전이라도 점유 만료 시각이 지났으면 RESERVATION_HOLD_EXPIRED 예외가 발생한다")
    void confirm_holdPastExpiry_throwsExpired() {
        // 만료 시각과 같은 순간부터 만료로 본다
        assertErrorCode(hold(NOW), DEPOSIT, NOW, ErrorCode.RESERVATION_HOLD_EXPIRED);
    }

    @Test
    @DisplayName("EXPIRED 예약을 확정하면 RESERVATION_HOLD_EXPIRED 예외가 발생한다")
    void confirm_expiredStatus_throwsExpired() {
        Reservation reservation = withStatus(ReservationStatus.EXPIRED);

        assertErrorCode(reservation, DEPOSIT, NOW, ErrorCode.RESERVATION_HOLD_EXPIRED);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"CANCEL_PENDING", "CANCELLED"})
    @DisplayName("취소 처리 중·취소 완료 예약을 확정하면 RESERVATION_NOT_HOLD 예외가 발생한다")
    void confirm_cancelled_throwsNotHold(ReservationStatus status) {
        assertErrorCode(withStatus(status), DEPOSIT, NOW, ErrorCode.RESERVATION_NOT_HOLD);
    }

    @Test
    @DisplayName("결제 금액이 예약금과 다르면 RESERVATION_DEPOSIT_MISMATCH 예외가 발생하고 상태는 그대로다")
    void confirm_amountMismatch_throwsWithoutChange() {
        Reservation reservation = hold(NOW.plusMinutes(5));

        assertErrorCode(reservation, DEPOSIT - 100, NOW, ErrorCode.RESERVATION_DEPOSIT_MISMATCH);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HOLD);
        assertThat(reservation.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("확정할 때 받은 요청사항은 앞뒤 공백을 제거해 저장한다")
    void confirm_withRequest_savesStripped() {
        Reservation reservation = hold(NOW.plusMinutes(5));

        reservation.confirm(DEPOSIT, NOW, "  손톱은 라운드로 부탁드려요  ");

        assertThat(reservation.getCustomerRequest()).isEqualTo("손톱은 라운드로 부탁드려요");
    }

    @Test
    @DisplayName("요청사항이 null 이면 예약 생성 때 받은 값을 유지하고, 공백뿐이면 비운다")
    void confirm_nullOrBlankRequest_keepsOrClears() {
        Reservation kept = hold(NOW.plusMinutes(5), "생성 때 요청");
        Reservation cleared = hold(NOW.plusMinutes(5), "생성 때 요청");

        kept.confirm(DEPOSIT, NOW, null);
        cleared.confirm(DEPOSIT, NOW, "   ");

        assertThat(kept.getCustomerRequest()).isEqualTo("생성 때 요청");
        assertThat(cleared.getCustomerRequest()).isNull();
    }

    @Test
    @DisplayName("요청사항은 1,000자까지 저장하고, 넘으면 INVALID_INPUT 예외가 발생하며 확정하지 않는다")
    void confirm_requestLengthBoundary() {
        Reservation atLimit = hold(NOW.plusMinutes(5));
        Reservation overLimit = hold(NOW.plusMinutes(5));

        atLimit.confirm(DEPOSIT, NOW, "가".repeat(Reservation.CUSTOMER_REQUEST_MAX_LENGTH));

        assertThat(atLimit.getCustomerRequest()).hasSize(Reservation.CUSTOMER_REQUEST_MAX_LENGTH);
        assertThatThrownBy(() -> overLimit.confirm(DEPOSIT, NOW,
                "가".repeat(Reservation.CUSTOMER_REQUEST_MAX_LENGTH + 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(overLimit.getStatus()).isEqualTo(ReservationStatus.HOLD);
        assertThat(overLimit.getConfirmedAt()).isNull();
        assertThat(overLimit.getCustomerRequest()).isNull();
    }

    private static Reservation hold(LocalDateTime holdExpiresAt) {
        return hold(holdExpiresAt, null);
    }

    private static Reservation hold(LocalDateTime holdExpiresAt, String customerRequest) {
        return Reservation.builder()
                .userId(1L)
                .treatmentId(1L)
                .startsAt(NOW.plusHours(3))
                .endsAt(NOW.plusHours(4))
                .appliedPrice(30_000)
                .deposit(DEPOSIT)
                .holdExpiresAt(holdExpiresAt)
                .customerRequest(customerRequest)
                .build();
    }

    private static Reservation withStatus(ReservationStatus status) {
        Reservation reservation = hold(NOW.plusMinutes(5));
        ReflectionTestUtils.setField(reservation, "status", status);
        return reservation;
    }

    private static void assertErrorCode(Reservation reservation, int paidAmount, LocalDateTime now,
            ErrorCode errorCode) {
        assertThatThrownBy(() -> reservation.confirm(paidAmount, now, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
