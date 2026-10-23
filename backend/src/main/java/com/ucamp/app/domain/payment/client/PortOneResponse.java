package com.ucamp.app.domain.payment.client;

/**
 * 포트원 V1 공통 응답 래퍼. code 가 0 이면 성공.
 */
public record PortOneResponse<T>(int code, String message, T response) {
}
