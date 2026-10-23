package com.ucamp.app.domain.treatment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.domain.treatment.dto.request.TreatmentSaveRequest;
import com.ucamp.app.domain.treatment.entity.Treatment;

class TreatmentTest {

    @ParameterizedTest
    @CsvSource({"50000,50000,60000,60000", "50000,30000,60000,30000", "40000,30000,40000,30000"})
    @DisplayName("정상가를 바꿔도 별도 할인가를 유지하며 미설정 가격만 정상가를 따른다")
    void updateRegularPrice_preservesConfiguredDiscounts(int price, int lastMinutePrice,
            int expectedPrice, int expectedLastMinutePrice) {
        Treatment treatment = discountedTreatment(price, lastMinutePrice);

        treatment.updateMenu(null, request(60000));

        assertThat(treatment.getPrice()).isEqualTo(expectedPrice);
        assertThat(treatment.getLastMinutePrice()).isEqualTo(expectedLastMinutePrice);
    }

    @ParameterizedTest
    @CsvSource({"40000,30000,35000", "50000,30000,20000"})
    @DisplayName("정상가를 낮춰 판매가·초임박가 순서가 깨지면 값을 바꾸지 않고 거절한다")
    void lowerRegularPrice_breaksPriceOrder_rejectsChange(int price, int lastMinutePrice, int nextRegularPrice) {
        Treatment treatment = discountedTreatment(price, lastMinutePrice);

        assertThatThrownBy(() -> treatment.updateMenu(null, request(nextRegularPrice)))
                .isInstanceOf(BusinessException.class);
        assertThat(treatment.getRegularPrice()).isEqualTo(50000);
        assertThat(treatment.getPrice()).isEqualTo(price);
        assertThat(treatment.getLastMinutePrice()).isEqualTo(lastMinutePrice);
    }

    private Treatment discountedTreatment(int price, int lastMinutePrice) {
        Treatment treatment = Treatment.create(null, null, request(50000));
        ReflectionTestUtils.setField(treatment, "price", price);
        ReflectionTestUtils.setField(treatment, "lastMinutePrice", lastMinutePrice);
        return treatment;
    }

    private TreatmentSaveRequest request(int regularPrice) {
        return new TreatmentSaveRequest("시술", "설명", regularPrice, 1L, 60);
    }
}
