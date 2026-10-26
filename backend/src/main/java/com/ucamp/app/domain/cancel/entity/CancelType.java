package com.ucamp.app.domain.cancel.entity;

/**
 * 취소 유형 (CNL-15).
 */
public enum CancelType {

    /** 고객 직접 취소. 정책 환불액(무료 취소 시 전액) */
    CUSTOMER,
    /** 매장 사유 취소. 납부 예약금 전액 환불 */
    SHOP_FAULT,
    /** 고객 요청 대리 취소. 사장님이 정책 환불액 ~ 납부 예약금 사이에서 환불액 입력 */
    CUSTOMER_REQUEST
}
