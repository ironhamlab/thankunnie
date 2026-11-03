package com.ucamp.app.domain.auth.dto.response;

import java.time.OffsetDateTime;

public record AuthPhoneResponse(String verificationId, OffsetDateTime expiresAt,
        OffsetDateTime resendAvailableAt, boolean verified) {
}
