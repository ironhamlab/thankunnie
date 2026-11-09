package com.ucamp.app.domain.explore.dto.response;

import java.math.BigDecimal;

import com.ucamp.app.domain.explore.repository.ExploreShopCandidate;

/**
 * 전체 매장 목록의 한 건. 빈자리 유무와 상관없이 노출 중인 매장 정보만 담는다.
 */
public record ExploreShopListResponse(
        Long shopId,
        String shopName,
        Long categoryId,
        String categoryName,
        String regionCode,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl) {

    public static ExploreShopListResponse from(ExploreShopCandidate shop) {
        return new ExploreShopListResponse(shop.shopId(), shop.shopName(), shop.categoryId(), shop.categoryName(),
                shop.regionCode(), shop.address(), shop.latitude(), shop.longitude(), shop.imageUrl());
    }
}
