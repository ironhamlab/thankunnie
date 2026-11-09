package com.ucamp.app.domain.payment.entity;

/**
 * 예약금결제 상태. DEPOSIT_PAYMENT.STATUS 의 DDL 주석 값과 철자를 맞춘다.
 */
public enum DepositPaymentStatus {
    READY,
    PAID,
    FAILED,
    UNKNOWN,
    REFUND_PENDING,
    PART_REFUNDED,
    REFUNDED;

    /**
     * 환불(취소)을 요청할 수 있는 상태인지. 결제 완료 또는 부분 환불된 건만 추가 환불할 수 있다.
     */
    public boolean refundable() {
        return this == PAID || this == PART_REFUNDED;
    }
}
