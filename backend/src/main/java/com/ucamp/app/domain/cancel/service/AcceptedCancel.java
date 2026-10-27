package com.ucamp.app.domain.cancel.service;

/**
 * 취소 접수 트랜잭션이 커밋된 뒤 환불 호출과 결과 반영·응답에 필요한 값.
 *
 * @param refundAmount  실제 환불할 금액. 0 원이면 결제 취소를 호출하지 않는다
 * @param closed        사장님이 취소하며 그 시간을 닫았는지
 * @param customerName  예약한 고객 이름 (사장님 응답용)
 * @param customerPhone 예약한 고객 전화번호 (사장님 응답용)
 */
public record AcceptedCancel(
        Long cancelId,
        Long reservationId,
        Long depositPaymentId,
        int refundAmount,
        boolean closed,
        String customerName,
        String customerPhone) {
}
