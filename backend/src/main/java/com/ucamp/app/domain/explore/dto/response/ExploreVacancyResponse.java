package com.ucamp.app.domain.explore.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.explore.repository.ExploreVacancyCandidate;
import com.ucamp.app.domain.vacancy.policy.PriceQuote;
import com.ucamp.app.domain.vacancy.policy.PriceTier;

/**
 * 탐색 목록의 빈자리 한 건 (C-01). 가격은 조회 시점 구간 기준 안내용이며, 예약 시 서버가 다시 계산한다.
 *
 * @param vacancyId       {treatmentId}-{yyyyMMddHHmm}. 목록 key 용
 * @param imageUrl        시술 이미지, 없으면 매장 이미지, 둘 다 없으면 null
 * @param bookingClosesAt 예약 클릭 마감 = 시작 20분 전
 */
public record ExploreVacancyResponse(
        String vacancyId,
        Long shopId,
        String shopName,
        String regionCode,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Long treatmentId,
        String treatmentName,
        Long categoryId,
        String categoryName,
        String treatmentType,
        int durationMinutes,
        String imageUrl,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime bookingClosesAt,
        PriceTier priceTier,
        int regularPrice,
        int salePrice,
        int discountRate,
        int depositAmount) {

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public static ExploreVacancyResponse of(ExploreVacancyCandidate candidate, LocalDateTime startsAt,
            PriceQuote quote) {
        String imageUrl = candidate.treatmentImageUrl() != null
                ? candidate.treatmentImageUrl()
                : candidate.shopImageUrl();
        return new ExploreVacancyResponse(
                candidate.treatmentId() + "-" + startsAt.format(ID_TIME),
                candidate.shopId(),
                candidate.shopName(),
                candidate.regionCode(),
                candidate.address(),
                candidate.latitude(),
                candidate.longitude(),
                candidate.treatmentId(),
                candidate.treatmentName(),
                candidate.categoryId(),
                candidate.categoryName(),
                candidate.treatmentType(),
                candidate.durationMinutes(),
                imageUrl,
                TimeUtils.toOffset(startsAt),
                TimeUtils.toOffset(startsAt.plusMinutes(candidate.durationMinutes())),
                TimeUtils.toOffset(startsAt.minus(BookingPolicy.BOOKING_DEADLINE)),
                quote.tier(),
                quote.regularPrice(),
                quote.salePrice(),
                quote.discountRate(),
                quote.depositAmount());
    }
}
