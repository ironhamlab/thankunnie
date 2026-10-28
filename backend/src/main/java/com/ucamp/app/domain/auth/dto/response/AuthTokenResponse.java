package com.ucamp.app.domain.auth.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.JwtProvider.IssuedToken;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.util.TimeUtils;

/**
 * 로그인·회원가입 성공 응답. 이후 요청에 Authorization: Bearer {accessToken} 을 붙인다.
 *
 * @param accessToken 액세스 토큰
 * @param tokenType   항상 Bearer
 * @param expiresAt   토큰 만료 시각 (+09:00)
 * @param userId      회원 ID. 사장님은 매장 ID 와 같다
 * @param role        역할
 * @param shopId      사장님의 매장 ID, 사장님이 아니면 null
 */
public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        OffsetDateTime expiresAt,
        Long userId,
        UserRole role,
        Long shopId) {

    private static final String BEARER = "Bearer";

    public static AuthTokenResponse of(IssuedToken token, AuthUser authUser) {
        return new AuthTokenResponse(
                token.accessToken(),
                BEARER,
                token.expiresAt().atZone(TimeUtils.KST).toOffsetDateTime(),
                authUser.userId(),
                authUser.role(),
                authUser.shopId());
    }
}
