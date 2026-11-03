package com.ucamp.app.domain.auth.entity;

/**
 * SHOP.STATUS 값. 매장 노출·판매 여부이며 사장님 로그인과는 관계없다.
 * 가용 시간 계산·알림 대상 조회가 ACTIVE 매장만 사용한다.
 */
public enum ShopAccountStatus {
    /** 관리자 승인 대기. 탐색에 노출되지 않는다 */
    PENDING,
    /** 관리자 거절. 사유는 REJECT_REASON 에 있다 */
    REJECTED,
    ACTIVE,
    INACTIVE
}
