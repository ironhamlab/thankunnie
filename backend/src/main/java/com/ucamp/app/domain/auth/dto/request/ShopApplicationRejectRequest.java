package com.ucamp.app.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 매장 가입 거절 요청.
 *
 * @param reason 매장에 보여줄 거절 사유
 */
public record ShopApplicationRejectRequest(
        @NotBlank @Size(max = 500) String reason) {
}
