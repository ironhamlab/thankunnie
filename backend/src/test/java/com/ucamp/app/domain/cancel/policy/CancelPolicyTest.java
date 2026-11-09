package com.ucamp.app.domain.cancel.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

class CancelPolicyTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final AuthUser CUSTOMER = new AuthUser(1L, UserRole.CUSTOMER, null);
    private static final AuthUser OWNER = new AuthUser(50L, UserRole.OWNER, 7L);
    private static final AuthUser ADMIN = new AuthUser(99L, UserRole.ADMIN, null);

    @Test
    @DisplayName("고객은 본인 예약, 매장은 본인 매장 예약을 취소할 수 있다")
    void validateRequester_ownReservation_passes() {
        assertThatCode(() -> CancelPolicy.validateRequester(CUSTOMER, 1L, 7L)).doesNotThrowAnyException();
        assertThatCode(() -> CancelPolicy.validateRequester(OWNER, 1L, 7L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("남의 예약·다른 매장 예약은 존재를 드러내지 않도록 찾을 수 없음으로 응답한다")
    void validateRequester_othersReservation_notFound() {
        assertErrorCode(() -> CancelPolicy.validateRequester(CUSTOMER, 2L, 7L), ErrorCode.CANCEL_RESERVATION_NOT_FOUND);
        assertErrorCode(() -> CancelPolicy.validateRequester(OWNER, 1L, 8L), ErrorCode.CANCEL_RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("관리자는 예약을 취소할 수 없다")
    void validateRequester_admin_forbidden() {
        assertErrorCode(() -> CancelPolicy.validateRequester(ADMIN, 1L, 7L), ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("고객은 직접 취소만, 매장은 매장 사유·고객 요청 대리 취소만 고를 수 있다")
    void validateCancelType_byRole() {
        assertThatCode(() -> CancelPolicy.validateCancelType(CUSTOMER, CancelType.CUSTOMER)).doesNotThrowAnyException();
        assertThatCode(() -> CancelPolicy.validateCancelType(OWNER, CancelType.SHOP_FAULT)).doesNotThrowAnyException();
        assertThatCode(() -> CancelPolicy.validateCancelType(OWNER, CancelType.CUSTOMER_REQUEST))
                .doesNotThrowAnyException();

        assertErrorCode(() -> CancelPolicy.validateCancelType(CUSTOMER, CancelType.SHOP_FAULT), ErrorCode.FORBIDDEN);
        assertErrorCode(() -> CancelPolicy.validateCancelType(OWNER, CancelType.CUSTOMER), ErrorCode.FORBIDDEN);
        assertErrorCode(() -> CancelPolicy.validateCancelType(ADMIN, CancelType.CUSTOMER), ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("확정 예약은 시작 1초 전까지 취소할 수 있다")
    void validateCancellable_confirmedBeforeStart_passes() {
        assertThatCode(() -> CancelPolicy.validateCancellable(ReservationStatus.CONFIRMED, STARTS_AT,
                STARTS_AT.minusSeconds(1))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시작 시각과 같거나 지나면 취소할 수 없다")
    void validateCancellable_atOrAfterStart_started() {
        assertErrorCode(() -> CancelPolicy.validateCancellable(ReservationStatus.CONFIRMED, STARTS_AT, STARTS_AT),
                ErrorCode.CANCEL_ALREADY_STARTED);
        assertErrorCode(() -> CancelPolicy.validateCancellable(ReservationStatus.CONFIRMED, STARTS_AT,
                STARTS_AT.plusMinutes(1)), ErrorCode.CANCEL_ALREADY_STARTED);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"CANCEL_PENDING", "CANCELLED"})
    @DisplayName("취소 처리 중·취소 완료 예약은 이미 취소 요청된 것으로 안내한다")
    void validateCancellable_alreadyCancelling(ReservationStatus status) {
        assertErrorCode(() -> CancelPolicy.validateCancellable(status, STARTS_AT, STARTS_AT.minusDays(1)),
                ErrorCode.CANCEL_ALREADY_REQUESTED);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"HOLD", "EXPIRED"})
    @DisplayName("점유·만료 예약은 확정 전이라 취소할 수 없다")
    void validateCancellable_notConfirmed(ReservationStatus status) {
        assertErrorCode(() -> CancelPolicy.validateCancellable(status, STARTS_AT, STARTS_AT.minusDays(1)),
                ErrorCode.CANCEL_NOT_CONFIRMED);
    }

    @Test
    @DisplayName("매장 사유 취소는 납부 예약금 전액을 환불한다")
    void shopFaultRefundAmount_isPaidAmount() {
        assertThat(CancelPolicy.shopFaultRefundAmount(20_000)).isEqualTo(20_000);
    }

    @ParameterizedTest(name = "환불액 {0}원 (정책 {1}원, 납부 {2}원) → 허용")
    @CsvSource({"0, 0, 20000", "20000, 0, 20000", "10000, 10000, 20000", "15000, 10000, 20000"})
    @DisplayName("대리 취소 환불액은 정책 환불액 이상, 납부 예약금 이하면 허용한다")
    void validateProxyRefundAmount_inRange(int refundAmount, int policyRefundAmount, int paidAmount) {
        assertThatCode(() -> CancelPolicy.validateProxyRefundAmount(refundAmount, policyRefundAmount, paidAmount))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "환불액 {0}원 (정책 {1}원, 납부 {2}원) → 거부")
    @CsvSource({"9900, 10000, 20000", "20100, 0, 20000", "-100, 0, 20000"})
    @DisplayName("대리 취소 환불액이 범위를 벗어나면 거부한다")
    void validateProxyRefundAmount_outOfRange(int refundAmount, int policyRefundAmount, int paidAmount) {
        assertErrorCode(() -> CancelPolicy.validateProxyRefundAmount(refundAmount, policyRefundAmount, paidAmount),
                ErrorCode.CANCEL_INVALID_REFUND_AMOUNT);
    }

    private void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}
