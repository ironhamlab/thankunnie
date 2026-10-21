package com.ucamp.app.common.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.ucamp.app.common.exception.ErrorCode;

/**
 * 모든 JSON 응답 본문의 공통 포맷.
 *
 * @param success 처리 성공 여부
 * @param data    성공 시 응답 데이터, 실패 시 null
 * @param error   실패 시 에러 정보, 성공 시 null
 * @param timestamp 응답 시각 (ISO-8601, +09:00)
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error, OffsetDateTime timestamp) {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, now());
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, ErrorResponse.of(errorCode), now());
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, ErrorResponse.of(errorCode, message), now());
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZONE_SEOUL).truncatedTo(ChronoUnit.SECONDS);
    }
}
