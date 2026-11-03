package com.ucamp.app.domain.user.entity;

/**
 * 약관 종류. TERMS.CODE 값과 같다.
 * 필수 여부는 코드가 아니라 TERMS.IS_REQUIRED 로 판단하고, 어느 계정의 약관인지는 코드마다 정해져 있다.
 */
public enum TermsCode {
    /** 만 14세 이상 확인 */
    AGE_14(TermsTarget.CUSTOMER),
    /** 서비스 이용약관 */
    SERVICE(TermsTarget.CUSTOMER),
    /** 개인정보 수집·이용 */
    PRIVACY(TermsTarget.CUSTOMER),
    /** 개인정보 제3자 제공 (예약한 매장에 이름·연락처 제공) */
    THIRD_PARTY(TermsTarget.CUSTOMER),
    /** 관심 시간 알림 문자 수신. 구독 등록 전에 필요하다 */
    SMS_NOTIFICATION(TermsTarget.CUSTOMER),
    /** 초보 시술 촬영·결과물 활용. 초보 시술 예약 전에 필요하다 */
    MODEL_PHOTO(TermsTarget.CUSTOMER),
    /** 매장 서비스 이용약관 */
    SHOP_SERVICE(TermsTarget.SHOP),
    /** 매장 개인정보 수집·이용 (사업자·대표자·매장 연락처) */
    SHOP_PRIVACY(TermsTarget.SHOP),
    /** 국세청 사업자 정보 조회 */
    SHOP_BUSINESS_CHECK(TermsTarget.SHOP),
    /** 매장 알림 문자 수신 */
    SHOP_SMS_NOTIFICATION(TermsTarget.SHOP);

    private final TermsTarget target;

    TermsCode(TermsTarget target) {
        this.target = target;
    }

    public TermsTarget getTarget() {
        return target;
    }
}
