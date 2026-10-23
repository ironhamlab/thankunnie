package com.ucamp.app.domain.shop.dto.response;

import java.math.BigDecimal;

import com.ucamp.app.domain.shop.entity.Shop;

/** 매장 관리 응답. */
public record ShopDetailResponse(
        Long shopId, Long categoryId, String name, String regionCode, String address, String addressDetail,
        BigDecimal latitude, BigDecimal longitude, String phone, String description, String notice, String imageUrl) {

    public static ShopDetailResponse from(Shop shop) {
        return new ShopDetailResponse(shop.getShopId(),
                shop.getCategoryId(),
                shop.getName(),
                shop.getRegionCode(),
                shop.getAddress(),
                shop.getAddressDetail(),
                shop.getLatitude(),
                shop.getLongitude(),
                shop.getPhone(),
                shop.getDescription(),
                shop.getNotice(),
                shop.getImageUrl());
    }
}
