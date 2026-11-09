package com.ucamp.app.domain.explore.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.explore.repository.ExploreVacancyDetailCandidate;
import com.ucamp.app.domain.vacancy.policy.PriceQuote;
import com.ucamp.app.domain.vacancy.policy.PriceTier;

/**
 * 빈자리 상세 (C-03). 목록(C-01) 필드에 예약 가능 여부·시술/매장 안내·잔금을 더한다.
 * 가격·예약 가능 여부는 조회 시점에 다시 계산한 안내용이며, 예약 시 서버가 다시 검사한다.
 *
 * @param vacancyId         {treatmentId}-{yyyyMMddHHmm}
 * @param imageUrl          시술 이미지, 없으면 매장 이미지, 둘 다 없으면 null
 * @param bookingClosesAt   예약 클릭 마감 = 시작 20분 전
 * @param bookable          지금 이 시각을 예약할 수 있는지
 * @param notBookableReason 예약할 수 없는 이유. bookable 이면 null
 * @param balanceAmount     현장 결제 잔금 = 판매가 - 예약금
 */
public record ExploreVacancyDetailResponse(
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
        int depositAmount,
        boolean bookable,
        ExploreNotBookableReason notBookableReason,
        String treatmentDescription,
        String modelCondition,
        String preparationNotes,
        String photoNotice,
        String addressDetail,
        String phone,
        String shopDescription,
        String shopNotice,
        int balanceAmount) {

    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public static ExploreVacancyDetailResponse of(ExploreVacancyDetailCandidate candidate, LocalDateTime startsAt,
            PriceQuote quote, ExploreNotBookableReason notBookableReason) {
        String imageUrl = candidate.treatmentImageUrl() != null
                ? candidate.treatmentImageUrl()
                : candidate.shopImageUrl();
        return new ExploreVacancyDetailResponse(
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
                candidate.treatmentType().name(),
                candidate.durationMinutes(),
                imageUrl,
                TimeUtils.toOffset(startsAt),
                TimeUtils.toOffset(startsAt.plusMinutes(candidate.durationMinutes())),
                TimeUtils.toOffset(startsAt.minus(BookingPolicy.BOOKING_DEADLINE)),
                quote.tier(),
                quote.regularPrice(),
                quote.salePrice(),
                quote.discountRate(),
                quote.depositAmount(),
                notBookableReason == null,
                notBookableReason,
                candidate.treatmentDescription(),
                candidate.modelCondition(),
                candidate.preparationNotes(),
                candidate.photoNotice(),
                candidate.addressDetail(),
                candidate.phone(),
                candidate.shopDescription(),
                candidate.shopNotice(),
                quote.balanceAmount());
    }
}
