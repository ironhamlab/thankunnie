package com.ucamp.app.domain.cancel.entity;

/**
 * 결제 취소(환불) 처리 상태.
 */
public enum CancelStatus {

    /** 취소 접수, 결제 취소 요청 전·중. 예약은 CANCEL_PENDING */
    REQUESTED,
    /** 결제 취소 성공. 예약은 CANCELLED */
    COMPLETED,
    /** 결제 취소 실패. 예약은 CONFIRMED 로 되돌리고 같은 행으로 재시도한다 */
    FAILED,
    /** 결제 취소 결과 불명. 확인 전까지 예약은 CANCEL_PENDING 으로 시간을 막아둔다 */
    UNKNOWN
}
