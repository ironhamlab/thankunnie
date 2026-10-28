package com.ucamp.app.domain.auth.dto.request;

import java.nio.charset.StandardCharsets;

/** 가입 요청의 입력 형식 규칙. 고객·사장님 가입이 같은 규칙을 쓴다. */
final class AuthInputRules {

    static final String LOGIN_ID = "^[a-z0-9]{4,20}$";
    static final String LOGIN_ID_MESSAGE = "아이디는 영문 소문자·숫자 4~20자로 입력해 주세요.";

    static final String PASSWORD_MESSAGE = "비밀번호는 8~64자로 입력해 주세요.";

    // BCrypt 는 UTF-8 72바이트를 넘는 비밀번호를 저장하지 못한다 (한글은 글자당 3바이트라 24자까지)
    static final int PASSWORD_MAX_BYTES = 72;
    static final String PASSWORD_BYTES_MESSAGE = "비밀번호가 너무 깁니다. 한글은 24자, 영문·숫자는 64자까지 입력할 수 있습니다.";

    // 010·011·016~019 휴대폰 번호. 하이픈은 있어도 없어도 된다
    static final String MOBILE_PHONE = "^01[016789]-?\\d{3,4}-?\\d{4}$";
    static final String MOBILE_PHONE_MESSAGE = "휴대폰 번호 형식이 올바르지 않습니다.";

    private AuthInputRules() {
    }

    /** null 은 @NotBlank 가 처리하므로 통과시킨다. */
    static boolean isPasswordWithinBytes(String password) {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
    }
}
