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
 * @param shopId 사장님의 매장 ID, 사장님이 아니면 null
 */
public record AuthUser(Long userId, UserRole role, Long shopId) {

    public AuthUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
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
     * 해당 매장의 사장님이 아니면 FORBIDDEN 예외를 던진다.
     */
    public void requireShopOwner(Long shopId) {
        if (!hasRole(UserRole.OWNER) || !Objects.equals(this.shopId, shopId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
