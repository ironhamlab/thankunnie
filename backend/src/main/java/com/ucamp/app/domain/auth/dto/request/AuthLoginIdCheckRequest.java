package com.ucamp.app.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AuthLoginIdCheckRequest(
        @NotBlank @Pattern(regexp = AuthInputRules.LOGIN_ID, message = AuthInputRules.LOGIN_ID_MESSAGE)
        String loginId,
        @NotBlank @Pattern(regexp = "customer|shop") String accountType) {
}
