package com.ucamp.app.domain.user.entity;

/**
 * 약관 종류. TERMS.CODE 값과 같다.
 * 필수 여부는 코드가 아니라 TERMS.IS_REQUIRED 로 판단한다.
 */
public enum TermsCode {
    /** 만 14세 이상 확인 */
    AGE_14,
    /** 서비스 이용약관 */
    SERVICE,
    /** 개인정보 수집·이용 */
    PRIVACY,
    /** 개인정보 제3자 제공 (예약한 매장에 이름·연락처 제공) */
    THIRD_PARTY,
    /** 관심 시간 알림 문자 수신. 구독 등록 전에 필요하다 */
    SMS_NOTIFICATION,
    /** 초보 시술 촬영·결과물 활용. 초보 시술 예약 전에 필요하다 */
    MODEL_PHOTO
}
