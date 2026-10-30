package com.ucamp.app.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.user.dto.response.UserProfileResponse;
import com.ucamp.app.domain.user.service.UserService;

/**
 * 회원 API. 고객·관리자만 사용할 수 있다.
 */
@Tag(name = "User", description = "회원")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 내 회원 정보를 조회한다.
     *
     * @param loginUser 로그인한 고객·관리자
     * @return 내 회원 정보
     */
    @Operation(summary = "내 회원 정보 조회", description = "이름·휴대폰 번호·가입 시각을 반환한다. 사장님은 403")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(@LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(userService.getMyProfile(loginUser)));
    }
}
