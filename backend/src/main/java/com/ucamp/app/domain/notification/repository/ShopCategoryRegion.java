package com.ucamp.app.domain.notification.repository;

/**
 * 카테고리·지역 구독과 비교할 매장 값. 엔티티가 아니라 조회 결과다.
 *
 * @param categoryId 매장 카테고리 ID
 * @param regionCode 매장 지역코드
 */
public record ShopCategoryRegion(Long categoryId, String regionCode) {
}
