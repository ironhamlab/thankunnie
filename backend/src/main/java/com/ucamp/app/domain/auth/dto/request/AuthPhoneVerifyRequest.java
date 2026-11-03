package com.ucamp.app.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AuthPhoneVerifyRequest(
        @NotBlank @Pattern(regexp = "[0-9a-f\\-]{36}") String verificationId,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {

    @Override
    public String toString() {
        return "AuthPhoneVerifyRequest[redacted]";
    }
}
