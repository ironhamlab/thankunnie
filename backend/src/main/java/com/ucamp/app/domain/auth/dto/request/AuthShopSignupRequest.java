package com.ucamp.app.domain.auth.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.ucamp.app.domain.user.entity.TermsCode;

/**
 * 매장 회원가입 요청. SHOP 의 필수 컬럼이 많아 계정과 매장 정보를 함께 받는다.
 * 매장 정보 검증 규칙은 매장 수정(ShopUpdateRequest)과 같다. 대표 이미지는 가입 후 이미지 API 로 올린다.
 *
 * @param regionCode          시군구 지역코드 (알림 매칭 기준)
 * @param phone               매장 휴대폰 번호. 가입 때는 휴대폰 인증을 받아 010-0000-0000 형식으로 저장한다.
 *                            고객과 달리 다른 매장·고객이 쓰는 번호여도 가입할 수 있다
 * @param agreedTerms         동의한 매장 약관 코드. 필수 매장 약관(SHOP_SERVICE·SHOP_PRIVACY·SHOP_BUSINESS_CHECK)은
 *                            모두 있어야 하고, 선택 약관(SHOP_SMS_NOTIFICATION)은 동의한 경우에만 넣는다
 * @param phoneVerificationId 휴대폰 인증 완료 ID
 */
public record AuthShopSignupRequest(
        @NotBlank @Pattern(regexp = AuthInputRules.LOGIN_ID, message = AuthInputRules.LOGIN_ID_MESSAGE)
        String loginId,
        @NotBlank @Size(min = 8, max = 64, message = AuthInputRules.PASSWORD_MESSAGE) String password,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 20) String regionCode,
        @NotBlank @Size(max = 300) String address,
        @Size(max = 200) String addressDetail,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
        @NotBlank @Pattern(regexp = AuthInputRules.MOBILE_PHONE, message = AuthInputRules.MOBILE_PHONE_MESSAGE)
        String phone,
        @Size(max = 2000) String description,
        @Size(max = 2000) String notice,
        @NotBlank @Pattern(regexp = "[0-9]{10}") String businessNumber,
        @NotBlank @Size(max = 100) String representativeName,
        @NotNull LocalDate openedOn,
        @NotNull List<@NotNull TermsCode> agreedTerms,
        @NotBlank @Size(max = 36) String phoneVerificationId) {

    @AssertTrue(message = AuthInputRules.PASSWORD_BYTES_MESSAGE)
    public boolean isPasswordWithinBytes() {
        return AuthInputRules.isPasswordWithinBytes(password);
    }

    @Override
    public String toString() {
        return "AuthShopSignupRequest[loginId=" + loginId + ", name=" + name + "]";
    }
}
