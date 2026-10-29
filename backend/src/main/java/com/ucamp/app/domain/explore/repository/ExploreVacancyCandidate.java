package com.ucamp.app.domain.explore.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ucamp.app.domain.treatment.entity.TreatmentType;

/**
 * 탐색 후보 한 줄. 사장님이 연 시간대(OPEN) 1건과 그 매장의 판매 중인 시술 1개를 묶은 것이다.
 * 실제 시작 가능 시각은 서비스에서 B 의 가용 시간 계산으로 거른다.
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
}
