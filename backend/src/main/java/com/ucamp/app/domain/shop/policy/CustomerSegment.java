package com.ucamp.app.domain.shop.policy;

/**
 * 매장 고객 분류. 이 매장의 누적 이용 추정 횟수(확정 예약 중 종료된 예약)로만 나눈다.
 * 고객 관리 배지와 분석 화면의 신규·재방문이 같은 기준을 쓴다.
 */
public enum CustomerSegment {

    /** 이용 0~1회 (첫 방문 예정 포함) */
    NEW,

    /** 이용 2회 이상 */
    RETURNING;

    /** 재방문으로 보는 최소 이용 횟수 */
    public static final int RETURNING_MIN_VISITS = 2;

    public static CustomerSegment of(long visitCount) {
        return visitCount >= RETURNING_MIN_VISITS ? RETURNING : NEW;
    }
}
