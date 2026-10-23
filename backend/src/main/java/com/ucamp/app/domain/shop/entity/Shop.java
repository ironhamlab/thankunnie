package com.ucamp.app.domain.shop.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.entity.BaseTimeEntity;
import com.ucamp.app.domain.shop.dto.request.ShopUpdateRequest;

/** 기존 사장님 계정의 매장 프로필. 인증 정보는 이 도메인에서 취급하지 않는다. */
@Entity
@Table(name = "SHOP")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shop extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_shop")
    @SequenceGenerator(name = "seq_shop", sequenceName = "SEQ_SHOP", allocationSize = 1)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SHOP_ID", nullable = false)
    private Long shopId;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "CATEGORY_ID", nullable = false)
    private Long categoryId;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "REGION_CODE", nullable = false, length = 20)
    private String regionCode;

    @Column(name = "ADDRESS", nullable = false, length = 300)
    private String address;

    @Column(name = "ADDRESS_DETAIL", length = 200)
    private String addressDetail;

    @Column(name = "LATITUDE", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "PHONE", nullable = false, length = 20)
    private String phone;

    @Column(name = "DESCRIPTION", length = 2000)
    private String description;

    @Column(name = "NOTICE", length = 2000)
    private String notice;

    @Column(name = "IMAGE_URL", length = 1000)
    private String imageUrl;

    /** 계정과 상태는 유지하고 매장 프로필을 저장한다. */
    public void updateProfile(ShopUpdateRequest request) {
        this.categoryId = request.categoryId();
        this.name = request.name();
        this.regionCode = request.regionCode();
        this.address = request.address();
        this.addressDetail = request.addressDetail();
        this.latitude = request.latitude();
        this.longitude = request.longitude();
        this.phone = request.phone();
        this.description = request.description();
        this.notice = request.notice();
        this.imageUrl = request.imageUrl();
    }
}
