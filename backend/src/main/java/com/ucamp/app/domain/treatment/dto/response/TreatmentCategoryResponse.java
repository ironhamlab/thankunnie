package com.ucamp.app.domain.treatment.dto.response;

import com.ucamp.app.domain.shop.entity.ShopCategory;

public record TreatmentCategoryResponse(Long categoryId, String name) {

    public static TreatmentCategoryResponse from(ShopCategory category) {
        return new TreatmentCategoryResponse(category.getCategoryId(), category.getName());
    }
}
