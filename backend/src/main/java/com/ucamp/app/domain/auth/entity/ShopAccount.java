package com.ucamp.app.domain.auth.entity;

import java.math.BigDecimal;

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
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.entity.BaseTimeEntity;

/**
 * 사장님 로그인 계정. SHOP 테이블에 매장 정보와 함께 있으며, 매장 프로필은 shop 도메인의 Shop 이 관리한다.
 *
 * <p>가입 시 SHOP 의 필수 컬럼을 한 번에 넣어야 해서 프로필 컬럼도 매핑한다. 가입 후 프로필 수정은 Shop 으로만 한다.
 * STATUS 는 가입 시 ACTIVE 로 넣기만 하고 로그인 판단에는 쓰지 않는다 (매장 노출·판매 여부).
 * 대표 이미지(IMAGE_URL)는 이미지 API 가 관리하므로 매핑하지 않고, 가입 시 null 로 남는다.
 */
@Entity
@Table(name = "SHOP")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopAccount extends BaseTimeEntity {

    // Shop 엔티티와 같은 시퀀스를 쓰되 generator 이름이 겹치지 않게 따로 둔다
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_shop_account")
    @SequenceGenerator(name = "seq_shop_account", sequenceName = "SEQ_SHOP", allocationSize = 1)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SHOP_ID", nullable = false)
    private Long shopId;

    @Column(name = "LOGIN_ID", nullable = false, length = 100, updatable = false)
    private String loginId;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private ShopAccountStatus status;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "CATEGORY_ID", nullable = false, updatable = false)
    private Long categoryId;

    @Column(name = "NAME", nullable = false, length = 100, updatable = false)
    private String name;

    @Column(name = "REGION_CODE", nullable = false, length = 20, updatable = false)
    private String regionCode;

    @Column(name = "ADDRESS", nullable = false, length = 300, updatable = false)
    private String address;

    @Column(name = "ADDRESS_DETAIL", length = 200, updatable = false)
    private String addressDetail;

    @Column(name = "LATITUDE", nullable = false, precision = 10, scale = 7, updatable = false)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", nullable = false, precision = 10, scale = 7, updatable = false)
    private BigDecimal longitude;

    @Column(name = "PHONE", nullable = false, length = 20, updatable = false)
    private String phone;

    @Column(name = "DESCRIPTION", length = 2000, updatable = false)
    private String description;

    @Column(name = "NOTICE", length = 2000, updatable = false)
    private String notice;

    /** 관리자가 입력한 가입 거절 사유. 거절 상태일 때만 값이 있다 */
    @Column(name = "REJECT_REASON", length = 500)
    private String rejectReason;

    @Builder
    private ShopAccount(String loginId, String passwordHash, Long categoryId, String name, String regionCode,
            String address, String addressDetail, BigDecimal latitude, BigDecimal longitude, String phone,
            String description, String notice) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.categoryId = categoryId;
        this.name = name;
        this.regionCode = regionCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.description = description;
        this.notice = notice;
        this.status = ShopAccountStatus.ACTIVE;
    }

    /** 가입을 승인해 매장을 노출 상태로 바꾼다. 승인 대기 여부는 호출한 쪽이 먼저 확인한다. */
    public void approve() {
        this.status = ShopAccountStatus.ACTIVE;
        this.rejectReason = null;
    }

    /** 가입을 거절하고 매장에 보여줄 사유를 남긴다. 승인 대기 여부는 호출한 쪽이 먼저 확인한다. */
    public void reject(String reason) {
        this.status = ShopAccountStatus.REJECTED;
        this.rejectReason = reason;
    }

    /** 잘못 거절한 신청을 승인 대기로 되돌리고 사유를 지운다. 거절 여부는 호출한 쪽이 먼저 확인한다. */
    public void restoreToPending() {
        this.status = ShopAccountStatus.PENDING;
        this.rejectReason = null;
    }

    /** 인증 공통 사용자로 바꾼다. 사장님은 APP_USER 행이 없으므로 사용자 ID 자리에 매장 ID 를 쓴다. */
    public AuthUser toAuthUser() {
        return new AuthUser(shopId, UserRole.OWNER, shopId);
    }
}
