package com.ucamp.app.common.auth;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * X-Test-User 헤더의 테스트 사용자를 SecurityContext 에 넣는 개발용 인증 필터.
 * SecurityConfig 에서 local·test 프로필일 때만 등록하며, 실제 로그인 도입 시 JWT 필터로 교체한다.
 * 헤더가 없거나 알 수 없는 값이면 비로그인으로 처리한다.
 */
@Slf4j
public class StubAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Test-User";

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String headerValue = request.getHeader(HEADER_NAME);
        Optional<TestUser> testUser = TestUser.from(headerValue);
        if (testUser.isPresent()) {
            authenticate(testUser.get().toAuthUser());
        } else if (headerValue != null) {
            log.debug("Unknown {} header: {}", HEADER_NAME, headerValue);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(AuthUser authUser) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                authUser, null, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + authUser.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
