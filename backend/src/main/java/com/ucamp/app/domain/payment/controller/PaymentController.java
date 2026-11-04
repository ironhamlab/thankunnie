package com.ucamp.app.domain.payment.controller;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.payment.dto.request.PaymentStartRequest;
import com.ucamp.app.domain.payment.dto.request.PaymentVerifyRequest;
import com.ucamp.app.domain.payment.dto.response.PaymentStartResponse;
import com.ucamp.app.domain.payment.dto.response.PaymentVerifyResponse;
import com.ucamp.app.domain.payment.service.PaymentService;
import com.ucamp.app.domain.payment.service.PaymentStartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentStartService paymentStartService;

    public PaymentController(PaymentService paymentService, PaymentStartService paymentStartService) {
        this.paymentService = paymentService;
        this.paymentStartService = paymentStartService;
    }

    /** 결제창을 열기 전에 이 화면이 결제를 맡는다. 다른 탭이 결제 중이면 409 PAYMENT_008 */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<PaymentStartResponse>> start(
            @LoginUser AuthUser loginUser, @Valid @RequestBody PaymentStartRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentStartService.start(loginUser, request)));
    }

    /** 결제창을 닫았거나 결제가 실패해 결제를 그만둔다. 다른 탭이 바로 결제할 수 있다 */
    @PostMapping("/start/cancel")
    public ResponseEntity<Void> abort(
            @LoginUser AuthUser loginUser, @Valid @RequestBody PaymentStartRequest request) {
        paymentStartService.abort(loginUser, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentVerifyResponse>> verify(
            @LoginUser AuthUser loginUser, @Valid @RequestBody PaymentVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.verify(loginUser, request)));
    }
}
