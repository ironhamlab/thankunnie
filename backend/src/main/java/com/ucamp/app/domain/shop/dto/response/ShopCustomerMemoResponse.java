package com.ucamp.app.domain.shop.dto.response;

import java.time.OffsetDateTime;

/**
 * @param updatedAt 마지막으로 저장한 시각
 */
public record ShopCustomerMemoResponse(String memo, OffsetDateTime updatedAt) {
}
