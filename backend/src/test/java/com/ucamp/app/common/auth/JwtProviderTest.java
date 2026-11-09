package com.ucamp.app.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.util.TimeUtils;

class JwtProviderTest {

    // 테스트 전용 더미 키
    private static final String SECRET = "CHANGE_ME-test-only-jwt-secret-000000";
    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");
    private static final Duration VALIDITY = Duration.ofHours(24);

    private final JwtProvider provider = providerAt(NOW);

    @Test
    @DisplayName("고객 토큰을 발급하고 같은 사용자로 복원한다")
    void issue_customer_parsesSameUser() {
        AuthUser customer = new AuthUser(1000L, UserRole.CUSTOMER, null);

        JwtProvider.IssuedToken token = provider.issue(customer);

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(VALIDITY));
        assertThat(provider.parse(token.accessToken())).contains(customer);
    }

    @Test
    @DisplayName("매장 토큰은 매장 ID 까지 복원한다")
    void issue_owner_keepsShopId() {
        AuthUser owner = new AuthUser(7L, UserRole.OWNER, 7L);

        assertThat(provider.parse(provider.issue(owner).accessToken())).contains(owner);
    }

    @Test
    @DisplayName("만료 시각까지는 받고 그 뒤에는 거부한다")
    void parse_afterExpiry_returnsEmpty() {
        String token = provider.issue(new AuthUser(1L, UserRole.CUSTOMER, null)).accessToken();

        assertThat(providerAt(NOW.plus(VALIDITY)).parse(token)).isPresent();
        assertThat(providerAt(NOW.plus(VALIDITY).plusSeconds(1)).parse(token)).isEmpty();
    }

    @Test
    @DisplayName("본문을 바꾸면 서명이 맞지 않아 거부한다")
    void parse_tamperedPayload_returnsEmpty() {
        String[] parts = provider.issue(new AuthUser(1L, UserRole.CUSTOMER, null)).accessToken().split("\\.");
        String forgedPayload = encode("{\"sub\":\"1\",\"role\":\"ADMIN\",\"exp\":9999999999}");

        assertThat(provider.parse(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    @DisplayName("alg 를 none 으로 바꾼 토큰을 거부한다")
    void parse_algNone_returnsEmpty() {
        String[] parts = provider.issue(new AuthUser(1L, UserRole.CUSTOMER, null)).accessToken().split("\\.");
        String noneHeader = encode("{\"alg\":\"none\",\"typ\":\"JWT\"}");

        assertThat(provider.parse(noneHeader + "." + parts[1] + ".")).isEmpty();
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰을 거부한다")
    void parse_otherSecret_returnsEmpty() {
        JwtProvider other = new JwtProvider("CHANGE_ME-another-test-jwt-secret-0000", VALIDITY, fixed(NOW));
        String token = other.issue(new AuthUser(1L, UserRole.CUSTOMER, null)).accessToken();

        assertThat(provider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("형식이 맞지 않는 값과 테스트 사용자 이름은 비로그인으로 본다")
    void parse_malformed_returnsEmpty() {
        assertThat(provider.parse(null)).isEmpty();
        assertThat(provider.parse("")).isEmpty();
        assertThat(provider.parse("OWNER_1")).isEmpty();
        assertThat(provider.parse("a.b.c")).isEmpty();
        assertThat(provider.parse("a.b.c.d")).isEmpty();
    }

    @Test
    @DisplayName("서명 키가 32바이트보다 짧으면 생성하지 않는다")
    void constructor_shortSecret_throws() {
        assertThatThrownBy(() -> new JwtProvider("short", VALIDITY, fixed(NOW)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JwtProvider providerAt(Instant instant) {
        return new JwtProvider(SECRET, VALIDITY, fixed(instant));
    }

    private static Clock fixed(Instant instant) {
        return Clock.fixed(instant, TimeUtils.KST);
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
