package com.ucamp.app.domain.auth.dto.response;

import com.ucamp.app.domain.shop.entity.ShopCategory;

/**
 * 매장 가입 화면의 카테고리 선택지.
 *
 * @param categoryId 카테고리 ID
 * @param name       카테고리명
 */
public record AuthShopCategoryResponse(Long categoryId, String name) {

    public static AuthShopCategoryResponse from(ShopCategory category) {
        return new AuthShopCategoryResponse(category.getCategoryId(), category.getName());
    }
}
