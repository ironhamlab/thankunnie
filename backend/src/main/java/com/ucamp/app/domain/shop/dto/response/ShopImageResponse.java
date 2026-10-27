package com.ucamp.app.domain.shop.dto.response;

/** 대표 이미지 저장 결과. imageUrl은 이미지 조회 API의 상대 경로다. */
public record ShopImageResponse(Long shopId, String imageUrl) {
}
