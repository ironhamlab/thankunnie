package com.ucamp.app.domain.explore.dto.response;

/**
 * 빈자리 상세에서 지금 예약할 수 없는 이유 (C-03).
 */
public enum ExploreNotBookableReason {

    /** 예약 클릭 마감(시작 20분 전)이 지났다 */
    BOOKING_CLOSED,
    /** 다른 예약·닫힌 시간·영업하지 않는 시간과 겹쳐 시작할 수 없다 */
    NOT_AVAILABLE
}
