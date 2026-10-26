package com.ucamp.app.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에러 코드 정의. 추가 시 docs/api/error-codes.md 에도 동일하게 기재한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // COMMON
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403", "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_405", "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),

    // NOTIFICATION
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_001", "구독을 찾을 수 없습니다."),
    SUBSCRIPTION_INVALID_TARGET(HttpStatus.BAD_REQUEST, "NOTIFICATION_002", "구독 유형에 맞지 않는 구독 대상입니다."),
    SUBSCRIPTION_INVALID_TIME(HttpStatus.BAD_REQUEST, "NOTIFICATION_003", "알림 희망 시간이 올바르지 않습니다."),

    // VACANCY
    VACANCY_BOOKING_CLOSED(HttpStatus.CONFLICT, "VACANCY_001", "예약 접수가 마감된 시간입니다."),
    VACANCY_PAYMENT_CLOSED(HttpStatus.CONFLICT, "VACANCY_002", "결제 마감 시간이 지났습니다."),
    VACANCY_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "VACANCY_003", "오늘부터 60일 이내만 예약할 수 있습니다."),
    VACANCY_INVALID_TIME_UNIT(HttpStatus.BAD_REQUEST, "VACANCY_004", "시각은 30분 단위여야 합니다."),
    VACANCY_INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "VACANCY_005", "종료 시각은 시작 시각보다 뒤여야 합니다."),
    VACANCY_TIME_TAKEN(HttpStatus.CONFLICT, "VACANCY_006", "이미 예약된 시간입니다."),
    VACANCY_INVALID_SALE_PRICE(HttpStatus.BAD_REQUEST, "VACANCY_007", "판매가는 100원 단위이며 정가 이하여야 합니다."),
    VACANCY_INVALID_LAST_MINUTE_PRICE(HttpStatus.BAD_REQUEST, "VACANCY_008", "초임박 판매가는 일반 판매가 이하여야 합니다."),
    VACANCY_OPEN_CLOSED(HttpStatus.BAD_REQUEST, "VACANCY_009", "시간대 열기는 시작 30분 전까지 등록할 수 있습니다."),
    VACANCY_SCHEDULE_EXCEPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "VACANCY_010", "시간대 열기·닫기 일정을 찾을 수 없습니다."),
    VACANCY_RESERVED_TIME(HttpStatus.CONFLICT, "VACANCY_011", "예약이 있는 시간은 닫을 수 없습니다."),
    VACANCY_INVALID_BUSINESS_DAYS(HttpStatus.BAD_REQUEST, "VACANCY_012", "영업시간은 월요일부터 일요일까지 한 번씩 입력해야 합니다."),
    VACANCY_NOT_OPEN_TIME(HttpStatus.CONFLICT, "VACANCY_013", "영업하지 않는 시간입니다."),
    VACANCY_BUSINESS_TIME_REQUIRED(HttpStatus.BAD_REQUEST, "VACANCY_014", "영업일은 시작·종료 시각을 입력해야 합니다."),
    VACANCY_INVALID_DURATION(HttpStatus.BAD_REQUEST, "VACANCY_015", "시술 소요 시간이 올바르지 않습니다."),

    // PAYMENT
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_PORTONE_ERROR(HttpStatus.BAD_GATEWAY, "PAYMENT_002", "결제 대행사 연동 중 오류가 발생했습니다."),

    // CANCEL
    CANCEL_RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CANCEL_001", "취소할 예약을 찾을 수 없습니다."),
    CANCEL_NOT_CONFIRMED(HttpStatus.CONFLICT, "CANCEL_002", "확정된 예약만 취소할 수 있습니다."),
    CANCEL_ALREADY_REQUESTED(HttpStatus.CONFLICT, "CANCEL_003", "이미 취소 요청된 예약입니다."),
    CANCEL_ALREADY_STARTED(HttpStatus.CONFLICT, "CANCEL_004", "시술 시작 이후에는 취소할 수 없습니다."),
    CANCEL_GRACE_ALREADY_USED(HttpStatus.CONFLICT, "CANCEL_005", "오늘 무료 취소를 이미 사용했습니다."),
    CANCEL_INVALID_REFUND_AMOUNT(HttpStatus.BAD_REQUEST, "CANCEL_006", "환불액은 정책 환불액 이상, 납부 예약금 이하여야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
