package com.ucamp.app.domain.cancel.policy;

/**
 * 무료 취소 예외(결제 후 10분 이내 전액 환불) 판정 결과. 고객 취소 화면(E-F1)의 안내 문구 분기에 사용한다.
 */
public enum GraceCancelResult {

    /** 무료 취소를 적용해 전액 환불하고, 오늘 무료 취소 횟수를 차감한다 */
    APPLIED,
    /** 기본 정책으로도 전액 환불이라 적용하지 않는다. 횟수를 차감하지 않는다 */
    NOT_NEEDED,
    /** 점유 시점에 시작까지 3시간 이하였던 초임박 예약이라 대상이 아니다 */
    LAST_MINUTE_HOLD,
    /** 결제 후 10분이 지났다 */
    WINDOW_PASSED,
    /** 오늘(한국 날짜) 무료 취소를 이미 사용했다 */
    ALREADY_USED_TODAY
}
