package com.ucamp.app.domain.shop.dto.response;

import java.math.BigDecimal;

import com.ucamp.app.domain.shop.entity.Shop;

/** 매장 정보 응답. */
public record ShopDetailResponse(Long shopId, String name, String description, String address,
        String addressDetail, BigDecimal latitude, BigDecimal longitude) {

    public static ShopDetailResponse from(Shop shop) {
        return new ShopDetailResponse(shop.getId(), shop.getName(), shop.getDescription(), shop.getAddress(),
                shop.getAddressDetail(), shop.getLatitude(), shop.getLongitude());
    }
}
