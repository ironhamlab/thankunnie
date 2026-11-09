package com.ucamp.app.common.policy;

import java.time.Duration;
import java.time.ZoneId;

import com.ucamp.app.common.util.TimeUtils;

/**
 * 빈자리 판매·예약 공통 정책값. 정책이 바뀌면 이 클래스만 수정한다.
 */
public final class BookingPolicy {

    /** 모든 시각 판단의 기준 시간대 */
    public static final ZoneId ZONE = TimeUtils.KST;

    /** 예약금 상한. 실제 예약금은 min(할인가, 이 값) */
    public static final int MAX_DEPOSIT_AMOUNT = 20_000;

    /** 매장 판매가 입력 단위(원) */
    public static final int PRICE_UNIT = 100;

    /** 예약 클릭 후 점유 유지 시간 */
    public static final Duration HOLD_DURATION = Duration.ofMinutes(5);

    /** 결제(접수) 마감: 시작 전 */
    public static final Duration PAYMENT_DEADLINE = Duration.ofMinutes(15);

    /** 예약 클릭 마감: 결제 마감 + 점유 시간 */
    public static final Duration BOOKING_DEADLINE = PAYMENT_DEADLINE.plus(HOLD_DURATION);

    /** 예약·시간대 열기·닫기 가능 기간(오늘 포함 일수) */
    public static final int BOOKING_MAX_DAYS = 60;

    /** 시간대 열기 등록 마감: 시작 전 */
    public static final Duration OPEN_REGISTRATION_DEADLINE = Duration.ofMinutes(30);

    /** 예약 시작 시각·예외 일정 시각 단위(분) */
    public static final int TIME_UNIT_MINUTES = 30;

    /** 임박 구간 시작: 시작 전 */
    public static final Duration IMMINENT_FROM = Duration.ofHours(8);

    /** 초임박 구간 시작: 시작 전 */
    public static final Duration LAST_MINUTE_FROM = Duration.ofHours(3);

    private BookingPolicy() {
    }
}
