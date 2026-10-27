package com.ucamp.app.domain.shop.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 사장님 계정에 연결된 매장 정보 전체 저장 요청. */
public record ShopUpdateRequest(
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 20) String regionCode,
        @NotBlank @Size(max = 300) String address,
        @Size(max = 200) String addressDetail,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
        @NotBlank @Size(max = 20) String phone,
        @Size(max = 2000) String description,
        @Size(max = 2000) String notice,
        @Null(message = "대표 이미지는 이미지 업로드·삭제 API를 사용해 주세요.") String imageUrl) {
}
