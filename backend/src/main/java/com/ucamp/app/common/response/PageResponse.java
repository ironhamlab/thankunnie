package com.ucamp.app.common.response;

import java.util.List;

/**
 * 페이지 목록 응답. 결과가 없거나 범위를 벗어난 페이지는 빈 content 와 실제 전체 개수를 돌려준다.
 *
 * @param content       현재 페이지 항목
 * @param page          0부터 시작하는 페이지 번호
 * @param size          페이지 크기
 * @param totalElements 조건에 맞는 전체 개수
 * @param totalPages    전체 페이지 수
 * @param hasNext       다음 페이지 존재 여부
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
        boolean hasNext) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages, page + 1 < totalPages);
    }
}
