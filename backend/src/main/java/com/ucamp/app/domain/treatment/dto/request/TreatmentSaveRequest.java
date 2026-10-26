package com.ucamp.app.domain.treatment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 시술 메뉴의 기본 정보와 구간별 판매가. */
public record TreatmentSaveRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        @NotNull @Positive Integer regularPrice,
        @NotNull Integer price,
        @NotNull Integer lastMinutePrice,
        @NotNull @Positive Long categoryId,
        @NotNull @Positive @Max(9990) Integer durationMinutes,
        @Valid TreatmentConditionsRequest conditions) {

    public TreatmentSaveRequest(String name, String description, Integer regularPrice, Integer price,
            Integer lastMinutePrice, Long categoryId, Integer durationMinutes) {
        this(name, description, regularPrice, price, lastMinutePrice, categoryId, durationMinutes, null);
    }

    @AssertTrue(message = "소요 시간은 30분 배수여야 합니다.")
    public boolean isDurationValid() {
        return durationMinutes == null || durationMinutes % 30 == 0;
    }

    @AssertTrue(message = "정상가는 100원 단위여야 합니다.")
    public boolean isRegularPriceValid() {
        return regularPrice == null || regularPrice % 100 == 0;
    }
}
