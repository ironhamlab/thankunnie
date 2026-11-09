package com.ucamp.app.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원 탈퇴 요청. 본인 확인을 위해 비밀번호를 다시 받는다.
 *
 * @param password 현재 비밀번호
 */
public record UserWithdrawRequest(@NotBlank @Size(max = 100) String password) {

    @Override
    public String toString() {
        return "UserWithdrawRequest[]";
    }
}
