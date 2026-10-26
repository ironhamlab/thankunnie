package com.ucamp.app.domain.treatment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.domain.treatment.dto.request.TreatmentSaveRequest;
import com.ucamp.app.domain.treatment.entity.Treatment;

class TreatmentTest {

    @ParameterizedTest
    @CsvSource({"50000,50000", "40000,30000", "40000,40000"})
    @DisplayName("등록과 수정에서 요청한 판매가·초임박가를 저장하며 같은 가격도 허용한다")
    void save_validPrices_persistsPrices(int price, int lastMinutePrice) {
        Treatment treatment = Treatment.create(null, null, request(50000, price, lastMinutePrice));
        assertThat(treatment.getPrice()).isEqualTo(price);
        assertThat(treatment.getLastMinutePrice()).isEqualTo(lastMinutePrice);

        treatment.updateMenu(null, request(60000, 45000, 35000));
        assertThat(treatment.getPrice()).isEqualTo(45000);
        assertThat(treatment.getLastMinutePrice()).isEqualTo(35000);
    }

    @ParameterizedTest
    @CsvSource({"50100,30000", "40001,30000", "40000,30001", "0,0", "-100,100", "40000,40100"})
    @DisplayName("잘못된 가격을 등록·수정에서 거절하고 기존 가격을 보존한다")
    void save_invalidPrices_rejectsWithoutMutation(int price, int lastMinutePrice) {
        assertThatThrownBy(() -> Treatment.create(null, null, request(50000, price, lastMinutePrice)))
                .isInstanceOf(BusinessException.class);
        Treatment treatment = Treatment.create(null, null, request(50000, 40000, 30000));
        assertThatThrownBy(() -> treatment.updateMenu(null, request(50000, price, lastMinutePrice)))
                .isInstanceOf(BusinessException.class);
        assertThat(treatment.getRegularPrice()).isEqualTo(50000);
        assertThat(treatment.getPrice()).isEqualTo(40000);
        assertThat(treatment.getLastMinutePrice()).isEqualTo(30000);
    }

    private TreatmentSaveRequest request(int regularPrice, int price, int lastMinutePrice) {
        return new TreatmentSaveRequest("시술", "설명", regularPrice, price, lastMinutePrice, 1L, 60);
    }
}
