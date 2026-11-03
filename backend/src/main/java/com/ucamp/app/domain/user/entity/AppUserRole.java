package com.ucamp.app.domain.user.entity;

import com.ucamp.app.common.auth.UserRole;

/**
 * APP_USER.ROLE 값. 매장은 회원이 아니라 매장(SHOP) 계정이므로 여기에 없다.
 */
public enum AppUserRole {
    USER(UserRole.CUSTOMER),
    ADMIN(UserRole.ADMIN);

    private final UserRole userRole;

    AppUserRole(UserRole userRole) {
        this.userRole = userRole;
    }

    /** 인증 공통 역할로 바꾼다. 일반 회원(USER)은 고객(CUSTOMER)이다. */
    public UserRole toUserRole() {
        return userRole;
    }
}
