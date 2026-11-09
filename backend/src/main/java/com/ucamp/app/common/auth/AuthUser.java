package com.ucamp.app.common.auth;

import java.util.Arrays;
import java.util.Objects;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

/**
 * 인증된 현재 사용자. 테스트 사용자 stub 과 이후 JWT 인증이 같은 형태로 SecurityContext 에 넣는다.
 *
 * @param userId 회원 ID
 * @param role   역할
 * @param shopId 매장 계정의 매장 ID, 매장 계정이 아니면 null
 */
public record AuthUser(Long userId, UserRole role, Long shopId) {

    public AuthUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        // 매장 계정은 계정당 매장 1개를 가진다. 매장 ID 없는 매장 계정은 권한 검사를 우회할 수 있어 생성 단계에서 막는다
        if (role == UserRole.OWNER && shopId == null) {
            throw new IllegalArgumentException("OWNER must have shopId");
        }
    }

    public boolean hasRole(UserRole role) {
        return this.role == role;
    }

    /**
     * 허용된 역할이 아니면 FORBIDDEN 예외를 던진다.
     */
    public void requireRole(UserRole... allowedRoles) {
        if (Arrays.stream(allowedRoles).noneMatch(this::hasRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 해당 매장 계정이 아니면 FORBIDDEN 예외를 던진다. 매장 ID 가 null 이면 항상 거부한다.
     */
    public void requireShopOwner(Long shopId) {
        if (shopId == null || !hasRole(UserRole.OWNER) || !shopId.equals(this.shopId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
