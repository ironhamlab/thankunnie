package com.ucamp.app.domain.explore.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 탐색 후보를 DB 에서 거르는 조건. 시각 관련 조건(시간대·마감 임박·가격 구간)은 시작 시각을 구한 뒤 서비스에서 거른다.
 *
 * @param from        조회 시작 시각(포함)
 * @param to          조회 종료 시각(미포함)
 * @param regionCodes 매장 지역 코드. 비어 있으면 전체
 * @param categoryId  시술 카테고리. null 이면 전체
 * @param shopId      매장. null 이면 전체
 * @param keyword     매장명·시술명 부분 일치. null 이면 전체
 * @param bounds      지도 화면 범위. null 이면 전체
 */
public record ExploreVacancyCondition(
        LocalDateTime from,
        LocalDateTime to,
        List<String> regionCodes,
        Long categoryId,
        Long shopId,
        String keyword,
        MapBounds bounds) {

    /**
     * 지도 화면 범위(위경도 사각형). 경계 위의 매장도 포함한다.
     */
    public record MapBounds(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng) {
    }
}
