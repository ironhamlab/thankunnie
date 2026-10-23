package com.ucamp.app.domain.vacancy.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;

class SalePolicyTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 18, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 9, 0);

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ReservationStatus.class, names = {"CONFIRMED", "CANCEL_PENDING"})
    @DisplayName("예약 완료·취소 처리 중 예약은 시간을 잡고 있다")
    void isOccupying_confirmedOrCancelPending_returnsTrue(ReservationStatus status) {
        assertThat(SalePolicy.isOccupying(status, NOW, NOW)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ReservationStatus.class, names = {"CANCELLED", "EXPIRED"})
    @DisplayName("취소·점유 만료 예약은 시간을 잡고 있지 않다")
    void isOccupying_cancelledOrExpired_returnsFalse(ReservationStatus status) {
        assertThat(SalePolicy.isOccupying(status, NOW.plusMinutes(5), NOW)).isFalse();
    }

    @Test
    @DisplayName("점유는 만료 시각 전까지만 시간을 잡고 있다")
    void isOccupying_hold_dependsOnExpiry() {
        assertThat(SalePolicy.isOccupying(ReservationStatus.HOLD, NOW.plusMinutes(3), NOW)).isTrue();
        assertThat(SalePolicy.isOccupying(ReservationStatus.HOLD, NOW, NOW)).isFalse();
    }

    @Test
    @DisplayName("만료 시각이 없는 점유는 시간을 잡고 있는 것으로 본다")
    void isOccupying_holdWithoutExpiry_returnsTrue() {
        assertThat(SalePolicy.isOccupying(ReservationStatus.HOLD, null, NOW)).isTrue();
    }

    @ParameterizedTest(name = "18:00~19:00 과 {0}~{1} → {2}")
    @CsvSource({
            "17:00, 18:00, false",
            "19:00, 20:00, false",
            "17:30, 18:30, true",
            "18:30, 19:30, true",
            "18:00, 19:00, true",
            "17:00, 20:00, true"
    })
    @DisplayName("종료 시각을 포함하지 않고 두 시간 구간의 겹침을 판단한다")
    void overlaps_returnsWhetherRangesIntersect(String otherStart, String otherEnd, boolean expected) {
        LocalDateTime endsAt = STARTS_AT.plusHours(1);
        LocalDateTime otherStartsAt = STARTS_AT.with(LocalTime.parse(otherStart));
        LocalDateTime otherEndsAt = STARTS_AT.with(LocalTime.parse(otherEnd));

        assertThat(SalePolicy.overlaps(STARTS_AT, endsAt, otherStartsAt, otherEndsAt)).isEqualTo(expected);
    }

    @Test
    @DisplayName("30분 단위이고 시작 20분 전 직전이며 60일 이내면 예약할 수 있다")
    void validateBookable_validStart_passes() {
        LocalDateTime justBefore = STARTS_AT.minusMinutes(20).minusSeconds(1);

        assertThatCode(() -> SalePolicy.validateBookable(STARTS_AT, justBefore)).doesNotThrowAnyException();
        assertThat(SalePolicy.isBookable(STARTS_AT, justBefore)).isTrue();
    }

    @Test
    @DisplayName("시작 시각이 30분 단위가 아니면 예약할 수 없다")
    void validateBookable_notOnUnit_throwsInvalidTimeUnit() {
        assertThatThrownBy(() -> SalePolicy.validateBookable(STARTS_AT.withMinute(15), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_TIME_UNIT);
    }

    @Test
    @DisplayName("시작 20분 전부터는 예약 클릭이 마감된다")
    void validateBookable_atDeadline_throwsBookingClosed() {
        assertThatThrownBy(() -> SalePolicy.validateBookable(STARTS_AT, STARTS_AT.minusMinutes(20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_BOOKING_CLOSED);
    }

    @Test
    @DisplayName("오늘 포함 60일 마지막 날까지 예약할 수 있고 그 다음 날은 예약할 수 없다")
    void validateBookable_after60Days_throwsOutOfRange() {
        LocalDateTime lastDay = LocalDateTime.of(2026, 12, 20, 23, 30);

        assertThatCode(() -> SalePolicy.validateBookable(lastDay, NOW)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SalePolicy.validateBookable(LocalDateTime.of(2026, 12, 21, 10, 0), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_OUT_OF_RANGE);
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

    @ParameterizedTest(name = "{0}")
    @EnumSource(ScheduleExceptionType.class)
    @DisplayName("30분 단위이고 종료가 시작보다 뒤이며 60일 이내인 열기·닫기는 등록할 수 있다")
    void validateExceptionTime_valid_passes(ScheduleExceptionType type) {
        assertThatCode(() -> SalePolicy.validateExceptionTime(type, STARTS_AT, STARTS_AT.plusDays(3), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("열기·닫기 시각이 30분 단위가 아니면 예외가 발생한다")
    void validateExceptionTime_notOnUnit_throwsInvalidTimeUnit() {
        assertThatThrownBy(() -> SalePolicy.validateExceptionTime(
                ScheduleExceptionType.CLOSE, STARTS_AT, STARTS_AT.plusMinutes(45), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_TIME_UNIT);
    }

    @Test
    @DisplayName("열기·닫기 종료가 시작보다 뒤가 아니면 예외가 발생한다")
    void validateExceptionTime_endNotAfterStart_throwsInvalidTimeRange() {
        assertThatThrownBy(() -> SalePolicy.validateExceptionTime(
                ScheduleExceptionType.CLOSE, STARTS_AT, STARTS_AT, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_TIME_RANGE);
    }

    @Test
    @DisplayName("오늘 포함 60일을 넘는 날짜는 열거나 닫을 수 없다")
    void validateExceptionTime_after60Days_throwsOutOfRange() {
        LocalDateTime tooFar = LocalDateTime.of(2026, 12, 21, 10, 0);

        assertThatThrownBy(() -> SalePolicy.validateExceptionTime(
                ScheduleExceptionType.CLOSE, tooFar, tooFar.plusHours(1), NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("열기는 시작 30분 전부터 등록할 수 없다")
    void validateExceptionTime_openAtDeadline_throwsOpenClosed() {
        assertThatThrownBy(() -> SalePolicy.validateExceptionTime(
                ScheduleExceptionType.OPEN, STARTS_AT, STARTS_AT.plusHours(2), STARTS_AT.minusMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_OPEN_CLOSED);
    }

    @Test
    @DisplayName("닫기는 시작 30분 전 이후에도 등록할 수 있다")
    void validateExceptionTime_closeNearStart_passes() {
        assertThatCode(() -> SalePolicy.validateExceptionTime(
                ScheduleExceptionType.CLOSE, STARTS_AT, STARTS_AT.plusHours(1), STARTS_AT.minusMinutes(10)))
                .doesNotThrowAnyException();
    }
}
