package com.ucamp.app.common.auth;

/**
 * JWT 로 인증할 사용자가 지금도 로그인할 수 있는 계정인지 확인한다.
 * 토큰은 서버에 저장하지 않으므로, 발급 뒤 계정 상태가 바뀌어도(매장 승인 취소 등) 만료 전까지 유효하다.
 * JwtAuthFilter 가 요청마다 이 검사를 거쳐, 통과하지 못한 토큰은 비로그인으로 처리한다.
 */
@FunctionalInterface
public interface AuthUserStatusChecker {

    /** 상태 검사기를 등록하지 않은 슬라이스 테스트용. 모든 토큰을 그대로 인증한다 */
    AuthUserStatusChecker ALLOW_ALL = authUser -> true;

    boolean canAuthenticate(AuthUser authUser);
}
