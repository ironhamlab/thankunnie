package com.ucamp.app.domain.payment.service;

import com.ucamp.app.domain.payment.client.PortOneClient;
import com.ucamp.app.domain.payment.dto.request.PaymentVerifyRequest;
import com.ucamp.app.domain.payment.dto.response.PaymentVerifyResponse;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PortOneClient portOneClient;

    public PaymentService(PortOneClient portOneClient) {
        this.portOneClient = portOneClient;
    }

    // DB 연동 전 단계: 포트원 조회 결과만 반환한다. 이후 주문 금액 비교·저장을 여기에 추가
    public PaymentVerifyResponse verify(PaymentVerifyRequest request) {
        return PaymentVerifyResponse.from(portOneClient.getPayment(request.impUid()));
    }
}
