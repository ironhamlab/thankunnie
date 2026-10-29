package com.ucamp.app.domain.explore.repository;

import java.math.BigDecimal;

/**
 * 매장 상세에 필요한 노출 중인 매장 정보.
 */
public record ExploreShopCandidate(
        Long shopId,
        String shopName,
        Long categoryId,
        String categoryName,
        String regionCode,
        String address,
        String addressDetail,
        BigDecimal latitude,
        BigDecimal longitude,
        String phone,
        String description,
        String notice,
        String imageUrl) {
}
