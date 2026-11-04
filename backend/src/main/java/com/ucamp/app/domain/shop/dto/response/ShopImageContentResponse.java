package com.ucamp.app.domain.shop.dto.response;

import java.time.Duration;

import org.springframework.http.CacheControl;

/** 이미지 바이너리 응답을 구성하는 내부 전송 객체. */
public record ShopImageContentResponse(byte[] content, String contentType) {

    /** 파일명은 업로드마다 새 UUID라 같은 URL의 내용이 바뀌지 않으므로, 재방문 때 다시 받지 않도록 오래 캐시한다. */
    public static final CacheControl CACHE_CONTROL =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
}
