package com.ucamp.app.domain.auth.dto.request;

/** 가입 요청의 입력 형식 규칙. 고객·사장님 가입이 같은 규칙을 쓴다. */
final class AuthInputRules {

    static final String LOGIN_ID = "^[a-z0-9]{4,20}$";
    static final String LOGIN_ID_MESSAGE = "아이디는 영문 소문자·숫자 4~20자로 입력해 주세요.";

    static final String PASSWORD_MESSAGE = "비밀번호는 8~64자로 입력해 주세요.";

    // 010·011·016~019 휴대폰 번호. 하이픈은 있어도 없어도 된다
    static final String MOBILE_PHONE = "^01[016789]-?\\d{3,4}-?\\d{4}$";
    static final String MOBILE_PHONE_MESSAGE = "휴대폰 번호 형식이 올바르지 않습니다.";

    private AuthInputRules() {
    }
}
