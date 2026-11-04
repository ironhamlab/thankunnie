package com.ucamp.app.domain.explore.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ucamp.app.domain.treatment.entity.TreatmentType;

/**
 * 탐색 후보 한 줄. 매장이 연 시간대(OPEN) 1건과 그 매장의 판매 중인 시술 1개를 묶은 것이다.
 * 실제 시작 가능 시각은 서비스에서 B 의 가용 시간 계산으로 거른다.
 * 임박 빈자리는 OPEN 없이 영업시간 기준으로 찾으므로 openStartsAt·openEndsAt 이 null 이다.
 * 빈자리 탐색의 초보 시술도 영업시간 기준이라, 매장이 열지 않았으면 null 이다.
 */
public record ExploreVacancyCandidate(
        LocalDateTime openStartsAt,
        LocalDateTime openEndsAt,
        Long shopId,
        String shopName,
        String regionCode,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String shopImageUrl,
        Long treatmentId,
        String treatmentName,
        Long categoryId,
        String categoryName,
        TreatmentType treatmentType,
        int durationMinutes,
        int regularPrice,
        int price,
        int lastMinutePrice,
        String treatmentImageUrl) {

    /**
     * 임박 빈자리 후보(매장 × 판매 중인 시술). JPQL 생성자 조회에서 쓴다.
     */
    public ExploreVacancyCandidate(Long shopId, String shopName, String regionCode, String address,
            BigDecimal latitude, BigDecimal longitude, String shopImageUrl, Long treatmentId, String treatmentName,
            Long categoryId, String categoryName, TreatmentType treatmentType, int durationMinutes, int regularPrice,
            int price, int lastMinutePrice, String treatmentImageUrl) {
        this(null, null, shopId, shopName, regionCode, address, latitude, longitude, shopImageUrl, treatmentId,
                treatmentName, categoryId, categoryName, treatmentType, durationMinutes, regularPrice, price,
                lastMinutePrice, treatmentImageUrl);
    }
}
