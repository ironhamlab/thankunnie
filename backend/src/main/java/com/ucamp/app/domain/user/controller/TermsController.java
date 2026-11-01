package com.ucamp.app.domain.user.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.user.dto.response.TermsResponse;
import com.ucamp.app.domain.user.dto.response.UserTermsAgreementResponse;
import com.ucamp.app.domain.user.entity.TermsCode;
import com.ucamp.app.domain.user.service.TermsAgreementService;

/**
 * 약관 API. 약관 목록은 로그인 없이 조회하고, 동의 조회·동의는 고객만 할 수 있다.
 * 회원가입 때의 동의는 회원가입 API 가 함께 저장한다.
 */
@Tag(name = "Terms", description = "약관·동의")
@RestController
@RequestMapping("/api/v1")
public class TermsController {

    private final TermsAgreementService termsAgreementService;

    public TermsController(TermsAgreementService termsAgreementService) {
        this.termsAgreementService = termsAgreementService;
    }

    @Operation(summary = "약관 목록 조회", description = "코드별 최신 버전 약관과 필수 여부. 로그인 없이 조회한다")
    @GetMapping("/terms")
    public ResponseEntity<ApiResponse<List<TermsResponse>>> getTerms() {
        return ResponseEntity.ok(ApiResponse.success(termsAgreementService.getTerms()));
    }

    @Operation(summary = "내 약관 동의 조회", description = "최신 버전 기준으로 동의한 약관 목록. 고객만 조회한다")
    @GetMapping("/users/me/terms-agreements")
    public ResponseEntity<ApiResponse<List<UserTermsAgreementResponse>>> getMyAgreements(
            @LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(termsAgreementService.getMyAgreements(loginUser)));
    }

    @Operation(summary = "약관 동의",
            description = "최신 버전 약관에 동의한다. 이미 동의했으면 기존 동의를 그대로 반환한다. "
                    + "문자 수신(SMS_NOTIFICATION)·초보 시술 촬영(MODEL_PHOTO) 동의 모달에서 호출한다")
    @PutMapping("/users/me/terms-agreements/{termsCode}")
    public ResponseEntity<ApiResponse<UserTermsAgreementResponse>> agree(
            @LoginUser AuthUser loginUser,
            @PathVariable TermsCode termsCode) {
        return ResponseEntity.ok(ApiResponse.success(termsAgreementService.agree(loginUser, termsCode)));
    }
}
