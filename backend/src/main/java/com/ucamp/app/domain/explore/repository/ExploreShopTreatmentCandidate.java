package com.ucamp.app.domain.explore.repository;

import com.ucamp.app.domain.treatment.entity.TreatmentType;

/**
 * 매장 상세에서 고를 수 있는 판매 중인 시술. 시작 시각별 가격은 빈자리 상세(C-03)가 계산한다.
 */
public record ExploreShopTreatmentCandidate(
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
}
