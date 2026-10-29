package com.ucamp.app.domain.explore.repository;

import java.math.BigDecimal;

import com.ucamp.app.domain.treatment.entity.TreatmentType;

/**
 * 빈자리 상세에 필요한 판매 중인 시술 1개와 그 매장 정보. 시작 시각별 예약 가능 여부·가격은 서비스에서 계산한다.
 */
public record ExploreVacancyDetailCandidate(
        Long shopId,
        String shopName,
        String regionCode,
        String address,
        String addressDetail,
        BigDecimal latitude,
        BigDecimal longitude,
        String phone,
        String shopDescription,
        String shopNotice,
        String shopImageUrl,
        Long treatmentId,
        String treatmentName,
        String treatmentDescription,
        Long categoryId,
        String categoryName,
        TreatmentType treatmentType,
        int durationMinutes,
        int regularPrice,
        int price,
        int lastMinutePrice,
        String modelCondition,
        String preparationNotes,
        String photoNotice,
        String treatmentImageUrl) {
}
