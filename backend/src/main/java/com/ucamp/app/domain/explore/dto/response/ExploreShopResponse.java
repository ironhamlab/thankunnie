package com.ucamp.app.domain.explore.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.ucamp.app.domain.vacancy.policy.PriceTier;

/**
 * 지도 마커용 매장 요약 한 건 (C-02). 조건에 맞는 그 매장 빈자리들의 최저가·최대 할인율·가장 빠른 시작 시각을 담는다.
 * 매장 상세는 일반 시술만, 초보 시술 모집 상세는 초보 시술만 보여주므로, 빈자리 수·가격·시작 시각은 카드가 연결되는 쪽
 * (일반 빈자리가 있으면 일반, 없으면 초보 시술) 빈자리로 계산한다.
 *
 * @param imageUrl          매장 이미지, 없으면 null
 * @param minSalePrice      마커 라벨 "18,000원~" 에 쓰는 최저 판매가
 * @param earliestPriceTier 가장 빠른 빈자리(earliestStartsAt)의 가격 구간. 지도 핀을 초임박·임박·일반으로 나눠 보여줄 때 쓴다
 * @param modelOnly         조건에 맞는 빈자리가 모두 초보 시술이면 true. 화면은 매장 상세 대신 초보 시술 모집 상세로 연결한다
 * @param modelVacancyCount 조건에 맞는 초보 시술 빈자리 수. 일반·초보 시술이 섞인 매장은 초보 시술 모집 상세 링크를 따로 보여준다
 * @param modelMinSalePrice 초보 시술 빈자리 최저 판매가. 초보 시술 빈자리가 없으면 null
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
        OffsetDateTime earliestStartsAt,
        PriceTier earliestPriceTier,
        boolean modelOnly,
        int modelVacancyCount,
        Integer modelMinSalePrice) {
}
