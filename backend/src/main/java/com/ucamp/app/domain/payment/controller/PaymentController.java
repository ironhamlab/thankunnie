package com.ucamp.app.domain.payment.controller;

import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.payment.dto.request.PaymentCancelRequest;
import com.ucamp.app.domain.payment.dto.request.PaymentVerifyRequest;
import com.ucamp.app.domain.payment.dto.response.PaymentCancelResponse;
import com.ucamp.app.domain.payment.dto.response.PaymentVerifyResponse;
import com.ucamp.app.domain.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentVerifyResponse>> verify(
            @Valid @RequestBody PaymentVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.verify(request)));
    }

    /**
     * 예약금결제 취소. 남은 금액을 전액 환불한다. 요청 본문은 생략할 수 있다.
     */
    @PostMapping("/{depositPaymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentCancelResponse>> cancel(
            @PathVariable Long depositPaymentId,
            @Valid @RequestBody(required = false) PaymentCancelRequest request) {
        String reason = (request == null ? new PaymentCancelRequest(null) : request).reasonOrDefault();
        return ResponseEntity.ok(ApiResponse.success(paymentService.cancel(depositPaymentId, reason)));
    }
}
