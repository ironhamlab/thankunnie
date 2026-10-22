package com.ucamp.app.domain.auth.dto.response;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;

/**
 * 현재 로그인 사용자 정보.
 *
 * @param userId 회원 ID
 * @param role   역할
 * @param shopId 사장님의 매장 ID, 사장님이 아니면 null
 */
public record AuthMeResponse(Long userId, UserRole role, Long shopId) {

    public static AuthMeResponse from(AuthUser authUser) {
        return new AuthMeResponse(authUser.userId(), authUser.role(), authUser.shopId());
    }
}
