package com.ucamp.app.domain.auth.entity;

/**
 * SHOP.STATUS 값. 매장 노출·판매 여부이며 매장 로그인과는 관계없다.
 * 가용 시간 계산·알림 대상 조회가 ACTIVE 매장만 사용한다.
 */
public enum ShopAccountStatus {
    ACTIVE,
    INACTIVE
}
