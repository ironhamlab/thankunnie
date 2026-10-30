package com.ucamp.app.domain.explore.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.ucamp.app.domain.explore.repository.ExploreShopCandidate;
import com.ucamp.app.domain.explore.repository.ExploreShopTreatmentCandidate;
import com.ucamp.app.domain.schedule.dto.response.BusinessHourResponse;
import com.ucamp.app.domain.treatment.entity.TreatmentType;

/**
 * 고객 매장 상세. 매장 안내, 월~일 영업시간, 판매 중인 시술 목록.
 * 날짜별 시작 가능 시각은 /api/v1/available-times, 시각별 가격은 빈자리 상세(C-03)로 조회한다.
 *
 * @param businessHours 월~일 7개. 저장하지 않은 요일은 휴무
 * @param treatments    판매 중인 시술, 시술 ID 순
 */
public record ExploreShopDetailResponse(
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
        String imageUrl,
        List<BusinessHourResponse> businessHours,
        List<ShopTreatment> treatments) {

    public static ExploreShopDetailResponse of(ExploreShopCandidate shop, List<BusinessHourResponse> businessHours,
            List<ExploreShopTreatmentCandidate> treatments) {
        return new ExploreShopDetailResponse(
                shop.shopId(),
                shop.shopName(),
                shop.categoryId(),
                shop.categoryName(),
                shop.regionCode(),
                shop.address(),
                shop.addressDetail(),
                shop.latitude(),
                shop.longitude(),
                shop.phone(),
                shop.description(),
                shop.notice(),
                shop.imageUrl(),
                businessHours,
                treatments.stream().map(ShopTreatment::from).toList());
    }

    /**
     * 판매 중인 시술.
     *
     * @param price          일반 판매가. 초임박 구간 가격은 시작 시각을 고른 뒤 빈자리 상세에서 확인한다
     * @param modelCondition 모델 모집 참여 조건. NORMAL 이면 null
     * @param photoNotice    모델 모집 촬영 안내. NORMAL 이면 null
     */
    public record ShopTreatment(
            Long treatmentId,
            String name,
            String description,
            TreatmentType treatmentType,
            int durationMinutes,
            int regularPrice,
            int price,
            String modelCondition,
            String photoNotice,
            String imageUrl) {

        static ShopTreatment from(ExploreShopTreatmentCandidate candidate) {
            return new ShopTreatment(
                    candidate.treatmentId(),
                    candidate.name(),
                    candidate.description(),
                    candidate.treatmentType(),
                    candidate.durationMinutes(),
                    candidate.regularPrice(),
                    candidate.price(),
                    candidate.modelCondition(),
                    candidate.photoNotice(),
                    candidate.imageUrl());
        }
    }
}
