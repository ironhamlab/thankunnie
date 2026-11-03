package com.ucamp.app.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.entity.BaseTimeEntity;

/** 회원(고객·관리자) 계정. 매장 계정은 매장(SHOP) 행에 있다. */
@Entity
@Table(name = "APP_USER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseTimeEntity {

    private static final String WITHDRAWN_NAME = "탈퇴회원";
    private static final String RELEASED_PHONE_PREFIX = "WD-";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_app_user")
    @SequenceGenerator(name = "seq_app_user", sequenceName = "SEQ_APP_USER", allocationSize = 1)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "LOGIN_ID", nullable = false, length = 100)
    private String loginId;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "NAME", nullable = false, length = 50)
    private String name;

    @Column(name = "PHONE", nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private AppUserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false, length = 10)
    private AppUserRole role;

    @Builder
    private AppUser(String loginId, String passwordHash, String name, String phone, AppUserRole role) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.status = AppUserStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == AppUserStatus.ACTIVE;
    }

    /**
     * 탈퇴한다. 예약 기록은 남기므로 행을 지우지 않고 상태만 바꾸며, 매장 화면에 보이는 이름은 가린다.
     * 휴대폰 번호는 탈퇴 계정으로 로그인할 때 안내하려고 같은 번호로 다시 가입하기 전까지 남긴다.
     */
    public void withdraw() {
        this.status = AppUserStatus.INACTIVE;
        this.name = WITHDRAWN_NAME;
    }

    /** 같은 번호로 다시 가입할 때 탈퇴 계정의 번호를 비운다. PHONE 은 NOT NULL·UNIQUE 라 회원별 값으로 바꾼다. */
    public void releasePhone() {
        this.phone = RELEASED_PHONE_PREFIX + userId;
    }

    /** 인증 공통 사용자로 바꾼다. 회원은 매장 ID 가 없다. */
    public AuthUser toAuthUser() {
        return new AuthUser(userId, role.toUserRole(), null);
    }
}
