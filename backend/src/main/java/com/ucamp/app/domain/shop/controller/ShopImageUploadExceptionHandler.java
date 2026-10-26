package com.ucamp.app.domain.shop.controller;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.response.ApiResponse;

/** 컨트롤러 선택 전 발생하는 multipart 용량 초과도 공통 400 응답으로 변환한다. */
@Order(-1)
@RestControllerAdvice
public class ShopImageUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT, "대표 이미지는 5MB 이하로 업로드해 주세요."));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedMultipart(MultipartException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.INVALID_INPUT,
                "올바른 multipart/form-data 요청으로 이미지를 전송해 주세요."));
    }
}
