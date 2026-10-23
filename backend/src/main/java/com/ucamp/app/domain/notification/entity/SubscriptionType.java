package com.ucamp.app.domain.notification.entity;

/**
 * 구독 유형. 업장(STORE) 또는 카테고리(CATEGORY_REGION).
 */
public enum SubscriptionType {

    /** 특정 매장 구독. 매장 ID만 있고 카테고리 ID·지역코드는 없다. */
    STORE,

    /** 카테고리 구독. 카테고리 ID와 지역코드가 있고 매장 ID는 없다. */
    CATEGORY_REGION
}
