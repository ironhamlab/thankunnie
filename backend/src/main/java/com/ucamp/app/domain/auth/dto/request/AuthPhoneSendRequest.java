package com.ucamp.app.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 휴대폰 인증번호 발송 요청.
 *
 * @param accountType 가입하려는 계정 종류 (customer·shop). 없으면 고객 가입으로 본다.
 *                    고객은 번호당 계정 하나라 이미 가입된 번호에 보내지 않고, 매장은 번호 중복을 허용한다
 */
public record AuthPhoneSendRequest(
        @NotBlank @Pattern(regexp = AuthInputRules.MOBILE_PHONE, message = AuthInputRules.MOBILE_PHONE_MESSAGE)
        String phone,
        @Pattern(regexp = "customer|shop") String accountType) {

    public boolean isForShop() {
        return "shop".equals(accountType);
    }
}
