package com.ucamp.app.common.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * HS256 JWT 액세스 토큰 발급·검증 (jjwt).
 *
 * <p>토큰에는 {@link AuthUser} 를 그대로 복원할 수 있는 사용자 ID(sub)·역할·매장 ID 만 담는다.
 * 검증은 서명된 토큰(JWS)만 받으므로 alg=none 같은 서명 없는 토큰은 거부된다.
 * 만료 판정은 서비스 Clock 을 쓴다.
 */
public class JwtProvider {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_SHOP_ID = "shopId";

    private final SecretKey key;
    private final Duration validity;
    private final Clock clock;
    private final JwtParser parser;

    public JwtProvider(String secret, Duration validity, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret(JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (validity == null || validity.isZero() || validity.isNegative()) {
            throw new IllegalStateException("app.jwt.access-token-validity must be positive");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validity = validity;
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    /** 현재 사용자로 액세스 토큰을 발급한다. */
    public IssuedToken issue(AuthUser authUser) {
        // JWT 시각 클레임은 초 단위라 응답의 만료 시각과 맞도록 초로 자른다
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(validity);

        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(authUser.userId()))
                .claim(CLAIM_ROLE, authUser.role().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));
        if (authUser.shopId() != null) {
            builder.claim(CLAIM_SHOP_ID, authUser.shopId());
        }
        String token = builder.signWith(key, Jwts.SIG.HS256).compact();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * 토큰을 검증하고 현재 사용자로 복원한다. 형식·서명·만료·클레임 중 하나라도 맞지 않으면 빈 값을 반환한다.
     * 실패 이유는 응답에 드러내지 않는다. 비로그인과 같게 처리되어 보호된 API 에서 401 이 된다.
     */
    public Optional<AuthUser> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            UserRole role = UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
            Number shopId = claims.get(CLAIM_SHOP_ID, Number.class);
            return Optional.of(new AuthUser(userId, role, shopId == null ? null : shopId.longValue()));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            // 서명·만료·형식 오류(JwtException), 숫자·역할 변환 실패, 클레임 누락, 매장 계정의 매장 ID 누락
            return Optional.empty();
        }
    }

    /**
     * 발급된 액세스 토큰.
     *
     * @param accessToken 토큰 문자열
     * @param expiresAt   만료 시각
     */
    public record IssuedToken(String accessToken, Instant expiresAt) {
    }
}
