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
    SUBSCRIPTION_INVALID_TIME(HttpStatus.BAD_REQUEST, "NOTIFICATION_003", "알림 희망 시간이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
