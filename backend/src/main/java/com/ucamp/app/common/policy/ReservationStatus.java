package com.ucamp.app.common.policy;

/**
 * 예약 행의 상태. 예약 행이 빈자리 역할도 하므로(사장님 등록 시 OPEN 으로 생성) 판매 상태를 함께 표현한다.
 * 점유 만료는 상태를 바로 바꾸지 않고 임시확보만료일시로 판단한다.
 */
public enum ReservationStatus {

    /** 판매 중: 고객 없음, 검색 노출 */
    OPEN,
    /** 점유: 예약 클릭 후 결제 대기 (5분) */
    HOLD,
    /** 예약 완료: 결제 확정 */
    CONFIRMED,
    /** 취소 처리 중 */
    CANCEL_PENDING,
    /** 중지: 사장님 판매 중지 또는 재공개 승인 대기 */
    CLOSED
}
