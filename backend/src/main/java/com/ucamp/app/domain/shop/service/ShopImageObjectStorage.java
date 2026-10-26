package com.ucamp.app.domain.shop.service;

/** 검증된 대표 이미지의 저장소. 파일명은 서버가 생성한 UUID 이름만 전달한다. */
public interface ShopImageObjectStorage {

    void put(String fileName, byte[] content, String contentType);

    byte[] read(String fileName);

    void delete(String fileName);
}
