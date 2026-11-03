package com.ucamp.app.common.auth;

import java.util.Optional;

/**
 * 실제 로그인 도입 전까지 사용하는 고정 테스트 사용자. local·test 프로필에서 X-Test-User 헤더 또는 Authorization: Bearer 값으로 선택한다.
 * ID 는 ERD 확정 후 초기 데이터(docs/erd/02_init_data_dml.sql)와 맞춘다.
 */
public enum TestUser {

    // 같은 시간 동시 예약·점유 확인용으로 고객 2명을 둔다
    CUSTOMER_1(1L, UserRole.CUSTOMER, null),
    CUSTOMER_2(2L, UserRole.CUSTOMER, null),
    // 다른 매장 권한 차단 확인용으로 매장 2명을 둔다
    OWNER_1(11L, UserRole.OWNER, 1L),
    OWNER_2(12L, UserRole.OWNER, 2L),
    ADMIN(21L, UserRole.ADMIN, null);

    private final AuthUser authUser;

    TestUser(Long userId, UserRole role, Long shopId) {
        this.authUser = new AuthUser(userId, role, shopId);
    }

    public AuthUser toAuthUser() {
        return authUser;
    }

    public static Optional<TestUser> from(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(name.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
