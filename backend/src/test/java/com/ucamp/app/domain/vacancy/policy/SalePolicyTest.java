package com.ucamp.app.domain.vacancy.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.ReservationStatus;

class SalePolicyTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 18, 0);
    private static final LocalDateTime NOW = STARTS_AT.minusHours(2);

    @Test
    @DisplayName("판매 중이고 시작 20분 전 직전까지는 판매 가능하다")
    void isSellable_openBeforeDeadline_returnsTrue() {
        LocalDateTime justBefore = STARTS_AT.minusMinutes(20).minusSeconds(1);

        assertThat(SalePolicy.isSellable(ReservationStatus.OPEN, null, STARTS_AT, justBefore)).isTrue();
    }

    @Test
    @DisplayName("시작 20분 전부터는 판매 중 상태여도 예약 클릭이 마감된다")
    void validateSellable_atDeadline_throwsBookingClosed() {
        assertThatThrownBy(() -> SalePolicy.validateSellable(
                ReservationStatus.OPEN, null, STARTS_AT, STARTS_AT.minusMinutes(20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_BOOKING_CLOSED);
    }

    @Test
    @DisplayName("점유 만료 전이면 다른 고객이 예약 중이라는 예외가 발생한다")
    void validateSellable_activeHold_throwsHeld() {
        LocalDateTime holdExpiresAt = NOW.plusMinutes(3);

        assertThatThrownBy(() -> SalePolicy.validateSellable(ReservationStatus.HOLD, holdExpiresAt, STARTS_AT, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_HELD);
    }

    @Test
    @DisplayName("점유 만료 시각이 지나면 상태가 HOLD 여도 다시 판매 가능하다")
    void isSellable_expiredHold_returnsTrue() {
        assertThat(SalePolicy.isSellable(ReservationStatus.HOLD, NOW, STARTS_AT, NOW)).isTrue();
    }

    @Test
    @DisplayName("만료 시각이 없는 점유는 판매하지 않는다")
    void isSellable_holdWithoutExpiry_returnsFalse() {
        assertThat(SalePolicy.isSellable(ReservationStatus.HOLD, null, STARTS_AT, NOW)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ReservationStatus.class, names = {"CONFIRMED", "CANCEL_PENDING", "CLOSED"})
    @DisplayName("예약 완료·취소 처리 중·중지 상태는 판매 중이 아니다")
    void validateSellable_notOpenStatus_throwsNotOnSale(ReservationStatus status) {
        assertThatThrownBy(() -> SalePolicy.validateSellable(status, null, STARTS_AT, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_NOT_ON_SALE);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ReservationStatus.class, names = {"OPEN", "CLOSED"})
    @DisplayName("판매 중·중지 상태는 사장님이 수정·중지할 수 있다")
    void validateEditable_openOrClosed_passes(ReservationStatus status) {
        assertThatCode(() -> SalePolicy.validateEditable(status, null, NOW)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ReservationStatus.class, names = {"CONFIRMED", "CANCEL_PENDING"})
    @DisplayName("예약 완료·취소 처리 중 상태는 수정·중지할 수 없다")
    void validateEditable_booked_throwsNotEditable(ReservationStatus status) {
        assertThatThrownBy(() -> SalePolicy.validateEditable(status, null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_NOT_EDITABLE);
    }

    @Test
    @DisplayName("점유 중이면 수정할 수 없고 점유가 만료되면 수정할 수 있다")
    void validateEditable_hold_dependsOnExpiry() {
        assertThatThrownBy(() -> SalePolicy.validateEditable(ReservationStatus.HOLD, NOW.plusMinutes(1), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_NOT_EDITABLE);
        assertThatCode(() -> SalePolicy.validateEditable(ReservationStatus.HOLD, NOW, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("점유 중 20분 전을 넘겨도 15분 전까지는 결제할 수 있다")
    void validatePayable_betweenBookingAndPaymentDeadline_passes() {
        assertThatCode(() -> SalePolicy.validatePayable(STARTS_AT, STARTS_AT.minusMinutes(16)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시작 15분 전부터는 결제가 마감된다")
    void validatePayable_atDeadline_throwsPaymentClosed() {
        assertThatThrownBy(() -> SalePolicy.validatePayable(STARTS_AT, STARTS_AT.minusMinutes(15)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_PAYMENT_CLOSED);
    }

    @Test
    @DisplayName("점유 만료 시각은 클릭 시점 5분 뒤다")
    void holdExpiresAt_returnsFiveMinutesLater() {
        assertThat(SalePolicy.holdExpiresAt(NOW)).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    @DisplayName("30분 단위이고 30분 전 이전이며 60일 이내면 등록할 수 있다")
    void validateRegistrable_validStart_passes() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 9, 0);
        LocalDateTime lastDay = LocalDateTime.of(2026, 12, 20, 23, 30);

        assertThatCode(() -> SalePolicy.validateRegistrable(now.plusHours(1), now)).doesNotThrowAnyException();
        assertThatCode(() -> SalePolicy.validateRegistrable(lastDay, now)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시작 시각이 30분 단위가 아니면 등록할 수 없다")
    void validateRegistrable_notOnUnit_throwsInvalidStartTime() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 9, 0);

        assertThatThrownBy(() -> SalePolicy.validateRegistrable(LocalDateTime.of(2026, 10, 23, 10, 15), now))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_START_TIME);
    }

    @Test
    @DisplayName("시작 30분 전부터는 등록할 수 없다")
    void validateRegistrable_atDeadline_throwsRegistrationClosed() {
        assertThatThrownBy(() -> SalePolicy.validateRegistrable(STARTS_AT, STARTS_AT.minusMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_REGISTRATION_CLOSED);
    }

    @Test
    @DisplayName("오늘 포함 60일을 넘는 날짜는 등록할 수 없다")
    void validateRegistrable_after60Days_throwsOutOfRange() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 9, 0);

        assertThatThrownBy(() -> SalePolicy.validateRegistrable(LocalDateTime.of(2026, 12, 21, 10, 0), now))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_OUT_OF_RANGE);
    }
}
