package com.ucamp.app.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentVerifyRequest(@NotBlank String impUid) {
}
