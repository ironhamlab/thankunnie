package com.ucamp.app.domain.auth.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.auth.dto.request.AuthLoginIdCheckRequest;
import com.ucamp.app.domain.auth.dto.request.AuthBusinessVerificationRequest;
import com.ucamp.app.domain.auth.dto.response.AuthBusinessVerificationResponse;
import com.ucamp.app.domain.auth.dto.request.AuthPhoneSendRequest;
import com.ucamp.app.domain.auth.dto.request.AuthPhoneVerifyRequest;
import com.ucamp.app.domain.auth.dto.response.AuthLoginIdCheckResponse;
import com.ucamp.app.domain.auth.dto.response.AuthPhoneResponse;
import com.ucamp.app.domain.auth.service.AuthService;
import com.ucamp.app.domain.auth.service.AuthPhoneVerificationService;
import com.ucamp.app.domain.auth.service.AuthBusinessVerificationService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthVerificationController {

    private final AuthService authService;
    private final AuthPhoneVerificationService verificationService;
    private final AuthBusinessVerificationService businessVerificationService;

    public AuthVerificationController(AuthService authService, AuthPhoneVerificationService verificationService,
            AuthBusinessVerificationService businessVerificationService) {
        this.authService = authService;
        this.verificationService = verificationService;
        this.businessVerificationService = businessVerificationService;
    }

    @PostMapping("/login-id/check")
    public ApiResponse<AuthLoginIdCheckResponse> checkLoginId(@Valid @RequestBody AuthLoginIdCheckRequest request) {
        return ApiResponse.success(new AuthLoginIdCheckResponse(
                authService.isLoginIdAvailable(request.loginId(), request.accountType())));
    }

    @PostMapping("/business/verify")
    public ApiResponse<AuthBusinessVerificationResponse> verifyBusiness(
            @Valid @RequestBody AuthBusinessVerificationRequest request) {
        businessVerificationService.verify(request);
        return ApiResponse.success(new AuthBusinessVerificationResponse(true));
    }

    @PostMapping("/phone/send")
    public ApiResponse<AuthPhoneResponse> send(@Valid @RequestBody AuthPhoneSendRequest request) {
        // 이미 가입된 고객 번호에는 문자를 보내지 않아 발송 비용과 가입 단계 낭비를 막는다.
        // 매장은 같은 번호로 여러 매장을 운영할 수 있어 중복을 검사하지 않는다.
        if (!request.isForShop()) {
            authService.validatePhoneAvailable(request.phone());
        }
        return ApiResponse.success(verificationService.send(request.phone()));
    }

    @PostMapping("/phone/verify")
    public ApiResponse<AuthPhoneResponse> verify(@Valid @RequestBody AuthPhoneVerifyRequest request) {
        return ApiResponse.success(verificationService.verify(request.verificationId(), request.code()));
    }
}
