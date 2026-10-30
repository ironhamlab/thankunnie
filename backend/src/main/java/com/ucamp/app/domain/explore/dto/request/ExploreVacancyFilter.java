package com.ucamp.app.domain.explore.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ucamp.app.domain.treatment.entity.TreatmentType;
import com.ucamp.app.domain.vacancy.policy.PriceTier;

/**
 * 빈자리 탐색 필터 (C-01 목록·C-02 지도 매장 요약 공용). 모두 선택값이며 null 이면 조건을 적용하지 않는다.
 * 범위 검사는 서비스에서 한다.
 *
 * @param shopId             한 매장의 빈자리만. 지도에서 매장 마커를 눌렀을 때 쓴다 (C-02 에서는 받지 않는다)
 * @param treatmentType      해당 시술 유형(일반·모델)만. 모델 모집 목록에서 MODEL 로 쓴다
 * @param closingWithinHours 예약 마감(시작 20분 전)이 지금부터 N시간 안인 빈자리만
 * @param priceTier          해당 가격 구간 이상만 (IMMINENT 면 임박·초임박)
 */
public record ExploreVacancyFilter(
        LocalDate startDate,
        LocalDate endDate,
        List<String> regionCodes,
        Long categoryId,
        Long shopId,
        TreatmentType treatmentType,
        ExploreTimeOfDay timeOfDay,
        Integer closingWithinHours,
        PriceTier priceTier,
        String keyword,
        BigDecimal minLat,
        BigDecimal maxLat,
        BigDecimal minLng,
        BigDecimal maxLng) {
}
