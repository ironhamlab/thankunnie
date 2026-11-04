package com.ucamp.app.domain.payment.dto.response;

/**
 * 결제 시작 응답.
 *
 * @param merchantUid 결제창에 넘길 주문번호. 같은 탭이 다시 시작하면 같은 값이다
 */
public record PaymentStartResponse(String merchantUid) {
}
