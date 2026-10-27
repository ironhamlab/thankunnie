package com.ucamp.app.domain.notification.client;

/**
 * 문자 한 건 발송 시도 결과.
 *
 * @param status            성공·실패·결과 불명
 * @param failReason        실패·결과 불명일 때 사유. 성공이면 null
 * @param providerMessageId 문자 서비스가 준 메시지 ID. 없으면 null
 */
public record SmsSendResult(SmsSendStatus status, String failReason, String providerMessageId) {

    public static SmsSendResult success(String providerMessageId) {
        return new SmsSendResult(SmsSendStatus.SUCCESS, null, providerMessageId);
    }

    public static SmsSendResult failed(String failReason) {
        return new SmsSendResult(SmsSendStatus.FAILED, failReason, null);
    }

    public static SmsSendResult unknown(String failReason) {
        return new SmsSendResult(SmsSendStatus.UNKNOWN, failReason, null);
    }
}
