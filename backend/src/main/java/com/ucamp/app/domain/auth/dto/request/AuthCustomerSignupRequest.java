package com.ucamp.app.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 고객 회원가입 요청.
 *
 * @param loginId  아이디 (영문 소문자·숫자 4~20자)
 * @param password 비밀번호 (8~64자)
 * @param name     이름
 * @param phone    휴대폰 번호. 하이픈 유무와 관계없이 받아 010-0000-0000 형식으로 저장한다. 문자 알림 수신 번호다
 */
public record AuthCustomerSignupRequest(
        @NotBlank @Pattern(regexp = AuthInputRules.LOGIN_ID, message = AuthInputRules.LOGIN_ID_MESSAGE)
        String loginId,
        @NotBlank @Size(min = 8, max = 64, message = AuthInputRules.PASSWORD_MESSAGE) String password,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = AuthInputRules.MOBILE_PHONE, message = AuthInputRules.MOBILE_PHONE_MESSAGE)
        String phone) {

    @Override
    public String toString() {
        return "AuthCustomerSignupRequest[loginId=" + loginId + ", name=" + name + "]";
    }
}
