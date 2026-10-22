package com.ucamp.app.common.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ucamp.app.common.response.ApiResponse;

/**
 * 인증 stub 테스트 전용 컨트롤러.
 */
@RestController
@RequestMapping("/test/auth")
class AuthTestController {

    @GetMapping("/me")
    ResponseEntity<ApiResponse<AuthUser>> me(@LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(loginUser));
    }

    @GetMapping("/optional")
    ResponseEntity<ApiResponse<String>> optional(@LoginUser(required = false) AuthUser loginUser) {
        String result = loginUser == null ? "anonymous" : loginUser.role().name();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/shops/{shopId}")
    ResponseEntity<Void> shop(@LoginUser AuthUser loginUser, @PathVariable Long shopId) {
        loginUser.requireShopOwner(shopId);
        return ResponseEntity.ok().build();
    }
}
