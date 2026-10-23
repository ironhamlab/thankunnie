package com.ucamp.app.domain.treatment.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 시술 메뉴의 기본 정보. 판매가·모델 조건은 각 담당 기능에서 관리한다. */
public record TreatmentSaveRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        @NotNull @Positive Integer regularPrice,
        @NotNull @Positive Long categoryId,
        @NotNull @Positive @Max(9990) Integer durationMinutes) {

    @AssertTrue(message = "소요 시간은 30분 배수여야 합니다.")
    public boolean isDurationValid() {
        return durationMinutes == null || durationMinutes % 30 == 0;
    }

    @AssertTrue(message = "정상가는 100원 단위여야 합니다.")
    public boolean isRegularPriceValid() {
        return regularPrice == null || regularPrice % 100 == 0;
    }
}
