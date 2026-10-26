package com.ucamp.app.domain.payment.service;

import com.ucamp.app.domain.payment.entity.DepositPaymentStatus;

/**
 * 환불 시작 트랜잭션이 커밋된 뒤 PG 호출과 결과 반영에 필요한 값.
 *
 * @param previousStatus 환불 시작 전 상태. PG 가 취소를 거절하면 이 상태로 되돌린다
 */
record RefundTarget(
        Long depositPaymentId,
        String impUid,
        int refundAmount,
        DepositPaymentStatus previousStatus
) {
}
