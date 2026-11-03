package com.ucamp.app.domain.user.entity;

/**
 * 약관을 받는 계정 종류. 고객과 매장은 가입 화면과 동의 저장 테이블이 다르다.
 * 고객 동의는 USER_TERMS_AGREEMENT, 매장 동의는 SHOP_TERMS_AGREEMENT 에 저장한다.
 */
public enum TermsTarget {
    CUSTOMER,
    SHOP
}
