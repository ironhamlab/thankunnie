package com.ucamp.app.domain.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.user.dto.request.UserWithdrawRequest;
import com.ucamp.app.domain.user.dto.response.UserProfileResponse;
import com.ucamp.app.domain.user.service.UserService;
import com.ucamp.app.domain.user.service.UserWithdrawalService;

/**
 * 회원 API. 고객·관리자만 사용할 수 있다.
 */
@Tag(name = "User", description = "회원")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserWithdrawalService userWithdrawalService;

    /**
     * 내 회원 정보를 조회한다.
     *
     * @param loginUser 로그인한 고객·관리자
     * @return 내 회원 정보
     */
    @Operation(summary = "내 회원 정보 조회", description = "이름·휴대폰 번호·가입 시각을 반환한다. 매장은 403")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(@LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(userService.getMyProfile(loginUser)));
    }

    /**
     * 회원 탈퇴한다. 성공하면 프론트가 세션을 지워 로그아웃한다.
     *
     * @param loginUser 로그인한 고객
     * @param request   본인 확인용 비밀번호
     * @return 204
     */
    @Operation(summary = "회원 탈퇴",
            description = "비밀번호를 확인한 뒤 탈퇴한다. 방문 전 예약·환불 처리 중인 취소가 있으면 409. 관리자·매장은 403")
    @PostMapping("/me/withdrawal")
    public ResponseEntity<Void> withdraw(@LoginUser AuthUser loginUser,
            @Valid @RequestBody UserWithdrawRequest request) {
        userWithdrawalService.withdraw(loginUser, request);
        return ResponseEntity.noContent().build();
    }
}
