package com.ucamp.app.domain.cancel.service;

/**
 * 취소 접수 트랜잭션이 커밋된 뒤 환불 호출과 결과 반영에 필요한 값.
 *
 * @param refundAmount 실제 환불할 금액. 0 원이면 결제 취소를 호출하지 않는다
 */
public record AcceptedCancel(
        Long cancelId,
        Long reservationId,
        Long depositPaymentId,
        int refundAmount) {
}
