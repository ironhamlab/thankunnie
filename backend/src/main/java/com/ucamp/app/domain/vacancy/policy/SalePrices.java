package com.ucamp.app.domain.vacancy.policy;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;

/**
 * 사장님이 빈자리에 입력한 구간별 최종 판매가(원). 임박 구간은 별도 할인 없이 일반 판매가를 쓴다.
 *
 * @param normal     일반·임박 구간 판매가
 * @param lastMinute 초임박 구간 판매가 (추가 할인)
 */
public record SalePrices(int normal, int lastMinute) {

    public int priceOf(PriceTier tier) {
        return switch (tier) {
            case NORMAL, IMMINENT -> normal;
            case LAST_MINUTE -> lastMinute;
        };
    }

    /**
     * 판매가 입력을 검사한다. 100원 단위, 0원 초과, 초임박 판매가 &lt; 일반 판매가 &lt; 정가.
     */
    public void validateFor(int regularPrice) {
        if (!isValidPrice(normal) || !isValidPrice(lastMinute) || normal >= regularPrice) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_SALE_PRICE);
        }
        if (lastMinute >= normal) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_LAST_MINUTE_PRICE);
        }
    }

    private static boolean isValidPrice(int price) {
        return price > 0 && price % BookingPolicy.PRICE_UNIT == 0;
    }
}
