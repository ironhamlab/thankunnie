package com.ucamp.app.common.auth;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * X-Test-User 헤더의 테스트 사용자를 SecurityContext 에 넣는 개발용 인증 필터.
 * 브라우저 화면은 axiosInstance 가 붙이는 Authorization: Bearer {테스트 사용자} 로도 보낼 수 있다. 둘 다 있으면 X-Test-User 가 우선한다.
 * SecurityConfig 에서 local·test 프로필일 때만 등록하며, 실제 로그인 도입 시 JWT 필터로 교체한다.
 * 헤더가 없거나 알 수 없는 값이면 비로그인으로 처리한다.
 */
@Slf4j
public class StubAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Test-User";
    public static final String BEARER_PREFIX = "Bearer ";

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String headerValue = resolveTestUserName(request);
        Optional<TestUser> testUser = TestUser.from(headerValue);
        if (testUser.isPresent()) {
            authenticate(testUser.get().toAuthUser());
        } else if (headerValue != null) {
            // Bearer 에 실제 토큰이 올 수 있으므로 값은 남기지 않는다
            log.debug("Unknown test user, treated as anonymous");
        }
        filterChain.doFilter(request, response);
    }

    private static String resolveTestUserName(HttpServletRequest request) {
        String testUserHeader = request.getHeader(HEADER_NAME);
        if (testUserHeader != null) {
            return testUserHeader;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private void authenticate(AuthUser authUser) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                authUser, null, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + authUser.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
