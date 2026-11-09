package com.ucamp.app.domain.vacancy.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

class PriceCalculatorTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 18, 0);
    private static final SalePrices SALE_PRICES = new SalePrices(30_000, 27_000);

    @Test
    @DisplayName("임박 구간은 일반 판매가로 할인 금액·할인율·예약금·잔금을 계산한다")
    void quote_imminent_usesNormalPrice() {
        PriceQuote quote = PriceCalculator.quote(35_000, SALE_PRICES, STARTS_AT, STARTS_AT.minusHours(5));

        assertThat(quote.tier()).isEqualTo(PriceTier.IMMINENT);
        assertThat(quote.salePrice()).isEqualTo(30_000);
        assertThat(quote.discountAmount()).isEqualTo(5_000);
        assertThat(quote.discountRate()).isEqualTo(14);
        assertThat(quote.depositAmount()).isEqualTo(20_000);
        assertThat(quote.balanceAmount()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("초임박 구간은 초임박 판매가를 적용한다")
    void quote_lastMinute_usesLastMinutePrice() {
        PriceQuote quote = PriceCalculator.quote(35_000, SALE_PRICES, STARTS_AT, STARTS_AT.minusHours(1));

        assertThat(quote.tier()).isEqualTo(PriceTier.LAST_MINUTE);
        assertThat(quote.salePrice()).isEqualTo(27_000);
        assertThat(quote.discountAmount()).isEqualTo(8_000);
        assertThat(quote.discountRate()).isEqualTo(22);
    }

    @Test
    @DisplayName("판매가가 2만 원보다 낮으면 판매가 전액이 예약금이고 잔금은 0원이다")
    void quote_salePriceUnderMaxDeposit_depositIsSalePrice() {
        PriceQuote quote = PriceCalculator.quote(20_000, new SalePrices(18_000, 15_000),
                STARTS_AT, STARTS_AT.minusDays(1));

        assertThat(quote.tier()).isEqualTo(PriceTier.NORMAL);
        assertThat(quote.discountRate()).isEqualTo(10);
        assertThat(quote.depositAmount()).isEqualTo(18_000);
        assertThat(quote.balanceAmount()).isZero();
    }

    @Test
    @DisplayName("구간별 미리보기는 일반·임박·초임박 순서이며 임박은 일반과 같은 가격이다")
    void preview_returnsAllTiersInOrder() {
        List<PriceQuote> quotes = PriceCalculator.preview(35_000, SALE_PRICES);

        assertThat(quotes).extracting(PriceQuote::tier)
                .containsExactly(PriceTier.NORMAL, PriceTier.IMMINENT, PriceTier.LAST_MINUTE);
        assertThat(quotes).extracting(PriceQuote::salePrice)
                .containsExactly(30_000, 30_000, 27_000);
    }

    @Test
    @DisplayName("판매가를 정가와 같게 입력하면 할인 없이 정가로 판매한다")
    void quote_noDiscount_sellsAtRegularPrice() {
        PriceQuote quote = PriceCalculator.quote(25_000, new SalePrices(25_000, 25_000),
                STARTS_AT, STARTS_AT.minusHours(1));

        assertThat(quote.salePrice()).isEqualTo(25_000);
        assertThat(quote.discountAmount()).isZero();
        assertThat(quote.discountRate()).isZero();
        assertThat(quote.depositAmount()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("초임박 판매가를 일반 판매가와 같게 입력하면 초임박 추가 할인이 없다")
    void quote_noLastMinuteDiscount_keepsNormalPrice() {
        PriceQuote quote = PriceCalculator.quote(25_000, new SalePrices(22_000, 22_000),
                STARTS_AT, STARTS_AT.minusHours(1));

        assertThat(quote.tier()).isEqualTo(PriceTier.LAST_MINUTE);
        assertThat(quote.salePrice()).isEqualTo(22_000);
    }

    @ParameterizedTest(name = "일반 {0}, 초임박 {1}")
    @CsvSource({
            "30050, 27000",
            "35100, 27000",
            "0, 0",
            "30000, -100"
    })
    @DisplayName("판매가가 100원 단위가 아니거나 0원 이하이거나 정가보다 비싸면 예외가 발생한다")
    void validate_invalidSalePrice_throwsException(int normal, int lastMinute) {
        SalePrices salePrices = new SalePrices(normal, lastMinute);

        assertThatThrownBy(() -> PriceCalculator.preview(35_000, salePrices))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_SALE_PRICE);
    }

    @ParameterizedTest(name = "일반 {0}, 초임박 {1}")
    @CsvSource({"30000, 30100", "30000, 31000"})
    @DisplayName("초임박 판매가가 일반 판매가보다 비싸면 예외가 발생한다")
    void validate_lastMinuteHigherThanNormal_throwsException(int normal, int lastMinute) {
        SalePrices salePrices = new SalePrices(normal, lastMinute);

        assertThatThrownBy(() -> PriceCalculator.preview(35_000, salePrices))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_LAST_MINUTE_PRICE);
    }
}
