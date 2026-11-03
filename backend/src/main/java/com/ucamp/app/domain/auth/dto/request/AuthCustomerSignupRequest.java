package com.ucamp.app.domain.auth.dto.request;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.ucamp.app.domain.user.entity.TermsCode;

/**
 * 고객 회원가입 요청.
 *
 * @param loginId  아이디 (영문 소문자·숫자 4~20자)
 * @param password 비밀번호 (8~64자)
 * @param name     이름
 * @param phone    휴대폰 번호. 하이픈 유무와 관계없이 받아 010-0000-0000 형식으로 저장한다. 문자 알림 수신 번호다
 * @param agreedTerms 동의한 약관 코드. 필수 약관(AGE_14·SERVICE·PRIVACY·THIRD_PARTY)은 모두 있어야 하고,
 *                    선택 약관(SMS_NOTIFICATION 등)은 동의한 경우에만 넣는다
 */
public record AuthCustomerSignupRequest(
        @NotBlank @Pattern(regexp = AuthInputRules.LOGIN_ID, message = AuthInputRules.LOGIN_ID_MESSAGE)
        String loginId,
        @NotBlank @Size(min = 8, max = 64, message = AuthInputRules.PASSWORD_MESSAGE) String password,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = AuthInputRules.MOBILE_PHONE, message = AuthInputRules.MOBILE_PHONE_MESSAGE)
        String phone,
        @NotNull List<@NotNull TermsCode> agreedTerms,
        @NotBlank @Size(max = 36) String phoneVerificationId) {

    @AssertTrue(message = AuthInputRules.PASSWORD_BYTES_MESSAGE)
    public boolean isPasswordWithinBytes() {
        return AuthInputRules.isPasswordWithinBytes(password);
    }

    @Override
    public String toString() {
        return "AuthCustomerSignupRequest[loginId=" + loginId + ", name=" + name + "]";
    }
}
