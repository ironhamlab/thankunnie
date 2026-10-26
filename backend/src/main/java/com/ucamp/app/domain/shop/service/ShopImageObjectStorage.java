package com.ucamp.app.domain.shop.service;

/** 검증된 대표 이미지의 저장소. 파일명은 서버가 생성한 UUID 이름만 전달한다. */
public interface ShopImageObjectStorage {

    /** 자격 증명을 제외한 저장소 식별자. 다른 버킷·경로에 대한 정리 작업 실행을 방지한다. */
    String scope();

    /** 외부 저장을 시도하기 전 확인 가능한 설정 오류는 정리 작업을 만들지 않고 거부한다. */
    default void checkReady() {
    }

    void put(String fileName, byte[] content, String contentType);

    byte[] read(String fileName);

    void delete(String fileName);
}
