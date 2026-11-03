package com.ucamp.app.domain.vacancy.policy;

/**
 * 한 구간의 가격 견적. 예약 클릭 시점에 PriceCalculator 로 계산하고, 예약금만 예약에 저장한다.
 *
 * @param tier           적용 구간
 * @param regularPrice   정가
 * @param discountAmount 할인 금액 (정가 - 판매가)
 * @param salePrice      매장이 입력한 구간 판매가
 * @param discountRate   할인율(%), 소수점 이하 버림
 * @param depositAmount  예약금, min(할인가, 20,000)
 * @param balanceAmount  현장 결제 잔금 (할인가 - 예약금)
 */
public record PriceQuote(
        PriceTier tier,
        int regularPrice,
        int discountAmount,
        int salePrice,
        int discountRate,
        int depositAmount,
        int balanceAmount) {
}
