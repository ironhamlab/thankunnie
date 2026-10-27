package com.ucamp.app.domain.shop.dto.response;

/** 이미지 바이너리 응답을 구성하는 내부 전송 객체. */
public record ShopImageContentResponse(byte[] content, String contentType) {
}
