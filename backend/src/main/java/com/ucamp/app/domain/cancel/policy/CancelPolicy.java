package com.ucamp.app.domain.cancel.policy;

import java.time.LocalDateTime;
import java.util.Objects;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 취소 가능 검사 (E-04)와 취소 유형별 환불액 규칙. DB·시계에 의존하지 않으며 현재 시각은 호출하는 쪽이 Clock 으로 구해 넘긴다.
 */
public final class CancelPolicy {

    private CancelPolicy() {
    }

    /**
     * 요청자가 이 예약을 취소할 수 있는 사람인지 검사한다.
     * 고객은 본인 예약, 사장님은 본인 매장 예약만 취소할 수 있다. 관리자는 취소하지 않는다.
     * 남의 예약은 존재 여부를 드러내지 않도록 찾을 수 없음(404)으로 응답한다.
     *
     * @param reservationUserId 예약한 고객 ID
     * @param reservationShopId 예약한 시술의 매장 ID
     */
    public static void validateRequester(AuthUser requester, Long reservationUserId, Long reservationShopId) {
        Objects.requireNonNull(requester, "requester");
        boolean allowed = switch (requester.role()) {
            case CUSTOMER -> requester.userId().equals(reservationUserId);
            case OWNER -> requester.shopId().equals(reservationShopId);
            case ADMIN -> throw new BusinessException(ErrorCode.FORBIDDEN);
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.CANCEL_RESERVATION_NOT_FOUND);
        }
    }

    /**
     * 요청자 역할로 고를 수 있는 취소 유형인지 검사한다. 고객은 CUSTOMER, 사장님은 SHOP_FAULT·CUSTOMER_REQUEST.
     */
    public static void validateCancelType(AuthUser requester, CancelType cancelType) {
        boolean allowed = requester.hasRole(UserRole.CUSTOMER)
                ? cancelType == CancelType.CUSTOMER
                : requester.hasRole(UserRole.OWNER) && cancelType != CancelType.CUSTOMER;
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 취소 접수 가능 여부. 확정(CONFIRMED) 예약이고 시술 시작 전이어야 한다 (CNL-12·16, REQ-052).
     * 취소 처리 중·취소 완료는 이미 접수된 것으로 구분해 안내한다.
     */
    public static void validateCancellable(ReservationStatus status, LocalDateTime startsAt, LocalDateTime now) {
        if (status == ReservationStatus.CANCEL_PENDING || status == ReservationStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.CANCEL_ALREADY_REQUESTED);
        }
        if (status != ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_CONFIRMED);
        }
        if (!now.isBefore(startsAt)) {
            throw new BusinessException(ErrorCode.CANCEL_ALREADY_STARTED);
        }
    }

    /**
     * 매장 사유 취소 환불액. 납부 예약금 전액 (CNL-17).
     */
    public static int shopFaultRefundAmount(int paidAmount) {
        return paidAmount;
    }

    /**
     * 고객 요청 대리 취소 환불액 검사. 고객 정책 환불액 이상, 납부 예약금 이하 (CNL-18).
     *
     * @param policyRefundAmount RefundPolicy 로 계산한 고객 정책 환불액 (무료 취소 미반영)
     */
    public static void validateProxyRefundAmount(int refundAmount, int policyRefundAmount, int paidAmount) {
        if (refundAmount < policyRefundAmount || refundAmount > paidAmount) {
            throw new BusinessException(ErrorCode.CANCEL_INVALID_REFUND_AMOUNT);
        }
    }
}
