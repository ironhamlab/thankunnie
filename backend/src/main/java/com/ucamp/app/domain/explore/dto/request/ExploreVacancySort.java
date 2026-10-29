package com.ucamp.app.domain.explore.dto.request;

/**
 * 탐색 목록 정렬. 같은 값이면 시작 시각, 시술 ID 순으로 이어서 정렬해 페이지 사이 순서를 고정한다.
 */
public enum ExploreVacancySort {
    /** 빠른 시작순 (홈의 "임박 빈자리 우선순"과 같다) */
    START_ASC,
    /** 할인율 높은 순 */
    DISCOUNT_DESC,
    /** 판매가 낮은 순 */
    PRICE_ASC
}
