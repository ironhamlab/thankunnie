package com.ucamp.app.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 고객·관리자 또는 사장님 로그인 요청. 형식 규칙은 가입 때만 검사하고, 로그인은 일치 여부만 본다.
 *
 * @param loginId  아이디
 * @param password 비밀번호
 */
public record AuthLoginRequest(
        @NotBlank @Size(max = 100) String loginId,
        @NotBlank @Size(max = 100) String password) {

    @Override
    public String toString() {
        return "AuthLoginRequest[loginId=" + loginId + "]";
    }
}
