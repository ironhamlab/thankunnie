package com.ucamp.app.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AuthPhoneSendRequest(
        @NotBlank @Pattern(regexp = AuthInputRules.MOBILE_PHONE, message = AuthInputRules.MOBILE_PHONE_MESSAGE)
        String phone) {
}
