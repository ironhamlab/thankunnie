package com.ucamp.app.domain.notification.repository;

/**
 * 매장에서 읽어 온 카테고리와 지역.
 * 테이블에 저장하는 줄이 아니라, 비교하려고 담아 둔 값이다.
 *
 * @param categoryId 매장 카테고리 ID
 * @param regionCode 매장 지역코드
 */
public record ShopCategoryRegion(Long categoryId, String regionCode) {
}
