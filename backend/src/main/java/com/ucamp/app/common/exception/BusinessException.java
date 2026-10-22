package com.ucamp.app.common.exception;

import lombok.Getter;

/**
 * 예상 가능한 업무 예외. GlobalExceptionHandler 에서 공통 에러 응답으로 변환된다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
