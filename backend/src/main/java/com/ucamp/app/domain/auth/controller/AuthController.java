package com.ucamp.app.domain.auth.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.auth.dto.request.AuthCustomerSignupRequest;
import com.ucamp.app.domain.auth.dto.request.AuthLoginRequest;
import com.ucamp.app.domain.auth.dto.request.AuthShopSignupRequest;
import com.ucamp.app.domain.auth.dto.response.AuthMeResponse;
import com.ucamp.app.domain.auth.dto.response.AuthShopCategoryResponse;
import com.ucamp.app.domain.auth.dto.response.AuthTokenResponse;
import com.ucamp.app.domain.auth.service.AuthService;

/**
 * 인증 API. 고객·관리자는 회원(APP_USER), 사장님은 매장(SHOP) 계정으로 가입·로그인한다.
 * 로그아웃은 서버 상태가 없으므로 클라이언트가 토큰을 지우는 것으로 처리한다.
 * local·test 프로필에서는 X-Test-User 테스트 사용자도 계속 쓸 수 있다.
 */
@Tag(name = "Auth", description = "인증")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "고객 회원가입", description = "가입 후 바로 로그인된 토큰을 반환한다. 아이디 중복이면 409")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signupCustomer(
            @Valid @RequestBody AuthCustomerSignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.signupCustomer(request)));
    }

    @Operation(summary = "고객·관리자 로그인", description = "아이디 또는 비밀번호가 틀리면 401")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> loginCustomer(@Valid @RequestBody AuthLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.loginCustomer(request)));
    }

    @Operation(summary = "사장님(매장) 회원가입", description = "계정과 매장 정보를 함께 저장하고 로그인된 토큰을 반환한다")
    @PostMapping("/shops/signup")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signupShop(
            @Valid @RequestBody AuthShopSignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.signupShop(request)));
    }

    @Operation(summary = "사장님(매장) 로그인", description = "아이디 또는 비밀번호가 틀리면 401")
    @PostMapping("/shops/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> loginShop(@Valid @RequestBody AuthLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.loginShop(request)));
    }

    @Operation(summary = "매장 가입용 카테고리 목록", description = "로그인 없이 조회한다")
    @GetMapping("/shop-categories")
    public ResponseEntity<ApiResponse<List<AuthShopCategoryResponse>>> getShopCategories() {
        return ResponseEntity.ok(ApiResponse.success(authService.getShopCategories()));
    }

    @Operation(summary = "현재 사용자 조회", description = "로그인 사용자의 ID·역할·매장 ID를 반환한다. 비로그인이면 401")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthMeResponse>> getMe(@LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(AuthMeResponse.from(loginUser)));
    }
}
