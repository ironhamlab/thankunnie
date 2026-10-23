package com.ucamp.app.domain.shop.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 매장 등록 및 전체 수정 요청. 매장 ID는 인증 정보에서 결정한다. */
public record ShopSaveRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 255) String address,
        @Size(max = 255) String addressDetail,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude) {
}
