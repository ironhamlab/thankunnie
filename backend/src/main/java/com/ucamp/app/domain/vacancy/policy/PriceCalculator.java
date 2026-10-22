package com.ucamp.app.domain.vacancy.policy;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import com.ucamp.app.common.policy.BookingPolicy;

/**
 * 빈자리 가격 계산 (B-05). 화면 가격은 안내용이며, 예약 시 서버가 quote 로 다시 계산한다.
 */
public final class PriceCalculator {

    private static final int PERCENT = 100;

    private PriceCalculator() {
    }

    /**
     * 판단 시점(예약 클릭 시점)의 구간으로 견적을 계산한다.
     */
    public static PriceQuote quote(int regularPrice, SalePrices salePrices,
            LocalDateTime startsAt, LocalDateTime now) {
        return quote(regularPrice, salePrices, PriceTier.of(startsAt, now));
    }

    /**
     * 점유·결제 이후 견적을 다시 계산한다. 견적은 저장하지 않고, 클릭 시점(점유 만료 - 점유 시간)과
     * 점유·예약 완료 중 수정이 막힌 행의 판매가로 복원한다. 결제 금액(예약금) 검증·환불 계산에 사용한다.
     * 재공개로 행이 다시 판매되면 이전 고객의 견적은 복원할 수 없다.
     */
    public static PriceQuote quoteAtHold(int regularPrice, SalePrices salePrices,
            LocalDateTime startsAt, LocalDateTime holdExpiresAt) {
        return quote(regularPrice, salePrices, startsAt, holdExpiresAt.minus(BookingPolicy.HOLD_DURATION));
    }

    /**
     * 모든 구간의 견적을 계산한다. 사장님 등록 화면의 구간별 미리보기(B-F4)에 사용한다.
     */
    public static List<PriceQuote> preview(int regularPrice, SalePrices salePrices) {
        return Arrays.stream(PriceTier.values())
                .map(tier -> quote(regularPrice, salePrices, tier))
                .toList();
    }

    private static PriceQuote quote(int regularPrice, SalePrices salePrices, PriceTier tier) {
        salePrices.validateFor(regularPrice);
        int salePrice = salePrices.priceOf(tier);
        int discountAmount = regularPrice - salePrice;
        // 할인율은 표시용이므로 실제보다 크게 보이지 않도록 버림한다
        int discountRate = (int) ((long) discountAmount * PERCENT / regularPrice);
        int depositAmount = Math.min(salePrice, BookingPolicy.MAX_DEPOSIT_AMOUNT);
        return new PriceQuote(tier, regularPrice, discountAmount, salePrice, discountRate,
                depositAmount, salePrice - depositAmount);
    }
}
