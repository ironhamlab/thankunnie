package com.ucamp.app.domain.explore.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 지도 마커용 매장 요약 한 건 (C-02). 조건에 맞는 그 매장 빈자리들의 최저가·최대 할인율·가장 빠른 시작 시각을 담는다.
 *
 * @param imageUrl     매장 이미지, 없으면 null
 * @param minSalePrice 마커 라벨 "18,000원~" 에 쓰는 최저 판매가
 */
public record ExploreShopResponse(
        Long shopId,
        String shopName,
        String regionCode,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl,
        int vacancyCount,
        int minSalePrice,
        int maxDiscountRate,
        OffsetDateTime earliestStartsAt) {
}
