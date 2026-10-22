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

    // VACANCY
    VACANCY_BOOKING_CLOSED(HttpStatus.CONFLICT, "VACANCY_001", "예약 접수가 마감된 빈자리입니다."),
    VACANCY_PAYMENT_CLOSED(HttpStatus.CONFLICT, "VACANCY_002", "결제 마감 시간이 지났습니다."),
    VACANCY_REGISTRATION_CLOSED(HttpStatus.BAD_REQUEST, "VACANCY_003", "시작 30분 전까지만 빈자리를 등록할 수 있습니다."),
    VACANCY_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "VACANCY_004", "오늘부터 60일 이내의 빈자리만 등록할 수 있습니다."),
    VACANCY_INVALID_START_TIME(HttpStatus.BAD_REQUEST, "VACANCY_005", "시작 시각은 30분 단위여야 합니다."),
    VACANCY_INVALID_SALE_PRICE(HttpStatus.BAD_REQUEST, "VACANCY_006", "판매가는 100원 단위이며 정가보다 낮아야 합니다."),
    VACANCY_INVALID_LAST_MINUTE_PRICE(HttpStatus.BAD_REQUEST, "VACANCY_007", "초임박 판매가는 일반 판매가보다 낮아야 합니다."),
    VACANCY_HELD(HttpStatus.CONFLICT, "VACANCY_008", "다른 고객이 예약 중인 빈자리입니다."),
    VACANCY_NOT_ON_SALE(HttpStatus.CONFLICT, "VACANCY_009", "판매 중인 빈자리가 아닙니다."),
    VACANCY_NOT_EDITABLE(HttpStatus.CONFLICT, "VACANCY_010", "예약 중이거나 예약 완료된 빈자리는 수정·중지할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
