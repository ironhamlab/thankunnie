package com.ucamp.app.domain.explore.dto.request;

/**
 * 빈자리 목록 조회 조건 (C-01). 필터에 정렬·페이지를 더한다.
 */
public record ExploreVacancySearchRequest(
        ExploreVacancyFilter filter,
        ExploreVacancySort sort,
        int page,
        int size) {
}
