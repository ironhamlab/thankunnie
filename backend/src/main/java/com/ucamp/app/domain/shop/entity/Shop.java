package com.ucamp.app.domain.shop.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.entity.BaseTimeEntity;

/** 인증에서 할당한 매장 ID를 사용하는 매장 정보. */
@Entity
@Table(name = "SHOP")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shop extends BaseTimeEntity {

    @Id
    @Column(name = "SHOP_ID")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 255)
    private String addressDetail;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Version
    @Column(nullable = false)
    private Long version;

    public Shop(Long id, String name, String description, String address, String addressDetail,
            BigDecimal latitude, BigDecimal longitude) {
        this.id = id;
        updateInformation(name, description, address, addressDetail, latitude, longitude);
    }

    /** 매장 정보 전체를 변경한다. */
    public void updateInformation(String name, String description, String address, String addressDetail,
            BigDecimal latitude, BigDecimal longitude) {
        this.name = name;
        this.description = description;
        this.address = address;
        this.addressDetail = addressDetail;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
