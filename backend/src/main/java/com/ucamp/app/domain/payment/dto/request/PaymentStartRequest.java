package com.ucamp.app.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 결제 시작·중단 요청. 결제창을 열기 전과, 결제창을 닫았거나 결제가 실패했을 때 보낸다.
 *
 * @param reservationId 결제할 예약
 * @param tabId         결제 화면(브라우저 탭)마다 하나인 임의 값. 같은 탭의 재시도는 통과시키고 다른 탭은 막는 데 쓴다
 */
public record PaymentStartRequest(
        @NotNull @Positive Long reservationId,
        @NotBlank @Size(max = 64) String tabId) {
}
