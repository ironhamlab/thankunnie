package com.ucamp.app.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 예약금 결제 확인 요청. 결제창이 끝나면 프론트가 보낸다.
 *
 * @param impUid          포트원 결제 고유번호
 * @param reservationId   결제한 예약. 결제창의 주문번호(merchant_uid)는 "reservation-{reservationId}-" 로 시작해야 한다
 * @param customerRequest 원장님께 남길 요청사항 (선택, 1,000자 이하). 예약이 확정될 때 함께 저장한다
 */
public record PaymentVerifyRequest(
        @NotBlank String impUid,
        @NotNull @Positive Long reservationId,
        @Size(max = 1000) String customerRequest) {
}
