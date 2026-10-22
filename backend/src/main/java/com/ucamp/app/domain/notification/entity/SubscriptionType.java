package com.ucamp.app.domain.notification.entity;

/**
 * 구독 유형.
 */
public enum SubscriptionType {

    /** 특정 매장 구독. SHOP_ID 필수 */
    STORE,

    /** 카테고리·지역 조건 구독. CATEGORY_CODE, REGION_CODE 필수 */
    CATEGORY_REGION
}
