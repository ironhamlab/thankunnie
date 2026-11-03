package com.ucamp.app.domain.shop.dto.request;

import jakarta.validation.constraints.Size;

import com.ucamp.app.domain.shop.entity.ShopCustomerMemo;

/**
 * @param memo 저장할 메모 (최대 1,000자). 비우거나 공백만 보내면 메모를 지운다
 */
public record ShopCustomerMemoRequest(
        @Size(max = ShopCustomerMemo.MEMO_MAX_LENGTH, message = "메모는 1,000자 이하여야 합니다.") String memo) {
}
