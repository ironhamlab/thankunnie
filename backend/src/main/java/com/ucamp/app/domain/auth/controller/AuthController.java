package com.ucamp.app.domain.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.auth.dto.response.AuthMeResponse;

/**
 * 인증 관련 API. 실제 로그인 도입 전에는 X-Test-User 헤더의 테스트 사용자를 기준으로 동작한다.
 */
@Tag(name = "Auth", description = "인증")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Operation(summary = "현재 사용자 조회", description = "로그인 사용자의 ID·역할·매장 ID를 반환한다. 비로그인이면 401")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthMeResponse>> getMe(@LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(AuthMeResponse.from(loginUser)));
    }
}
