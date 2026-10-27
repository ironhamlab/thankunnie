package com.ucamp.app.domain.notification.client;

/**
 * 문자 API 호출 결과. 발송 대기 작업 상태(SENT, FAILED, UNKNOWN)에 맞춰 쓴다.
 */
public enum SmsSendStatus {

    /** 문자 서비스가 발송 요청을 받았다 */
    SUCCESS,

    /** 문자 서비스가 발송을 거절했거나 설정이 잘못됐다 */
    FAILED,

    /** 응답을 받지 못해 실제 발송 여부를 알 수 없다. 성공으로 보지 않는다 */
    UNKNOWN
}
