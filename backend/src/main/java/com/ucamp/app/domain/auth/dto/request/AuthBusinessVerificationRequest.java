package com.ucamp.app.domain.auth.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthBusinessVerificationRequest(
        @NotBlank @Pattern(regexp = "[0-9]{10}", message = "사업자등록번호는 숫자 10자리로 입력해 주세요.")
        String businessNumber,
        @NotBlank @Size(max = 100) String representativeName,
        @NotNull LocalDate openedOn) {

    @Override
    public String toString() {
        return "AuthBusinessVerificationRequest[redacted]";
    }
}
