package com.ucamp.app.domain.notification.entity;

/**
 * 발송 대기 작업 상태.
 */
public enum NotificationDispatchStatus {

    /** 아직 아무도 가져가지 않은 대기 */
    PENDING,

    /** 한 프로세스가 가져간 처리 중. 서버가 다시 떠도 대기로 되돌리지 않는다 */
    PROCESSING,

    /** 발송 직전 재검사에 걸려 보내지 않고 끝낸 작업 */
    SKIPPED,

    /** 문자 발송이 끝난 작업 */
    SENT,

    /** 문자 발송이 실패한 작업. FAIL_REASON 에 사유를 둔다 */
    FAILED,

    /** 문자 API 응답이 애매해 발송 여부를 다시 봐야 하는 작업 */
    UNKNOWN
}
