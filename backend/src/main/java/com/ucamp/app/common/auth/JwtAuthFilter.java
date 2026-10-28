package com.ucamp.app.common.auth;

import java.io.IOException;
import java.util.List;

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

/**
 * Authorization: Bearer {JWT} 를 검증해 현재 사용자를 SecurityContext 에 넣는다.
 * 모든 프로필에서 동작하며, local·test 에서는 테스트 사용자 stub 이 먼저 인증한 요청을 건너뛴다.
 * 토큰이 없거나 유효하지 않으면 비로그인으로 처리하고, @LoginUser 가 필요한 API 에서 401 이 된다.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtProvider jwtProvider;

    public JwtAuthFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
                jwtProvider.parse(authorization.substring(BEARER_PREFIX.length()).trim())
                        .ifPresent(JwtAuthFilter::authenticate);
            }
        }
        filterChain.doFilter(request, response);
    }

    private static void authenticate(AuthUser authUser) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                authUser, null, List.of(new SimpleGrantedAuthority(ROLE_PREFIX + authUser.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
