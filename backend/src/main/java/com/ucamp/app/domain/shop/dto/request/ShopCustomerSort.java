package com.ucamp.app.domain.shop.dto.request;

/** 고객 목록 정렬. 같으면 최근 이용 순, 그다음 가까운 예정 예약 순 */
public enum ShopCustomerSort {
    /** 최근 이용 순 (이용이 없는 고객은 뒤로) */
    RECENT,
    /** 이용 횟수 많은 순 */
    VISITS,
    /** 누적 금액 많은 순 */
    AMOUNT
}
