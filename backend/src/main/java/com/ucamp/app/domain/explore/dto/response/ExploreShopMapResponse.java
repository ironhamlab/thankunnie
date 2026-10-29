package com.ucamp.app.domain.explore.dto.response;

import java.util.List;

/**
 * 지도 매장 요약 응답 (C-02).
 *
 * @param hasMore 한 번에 돌려주는 최대 매장 수를 넘었으면 true. 지도에 "확대하면 더 보여요" 를 표시한다
 */
public record ExploreShopMapResponse(List<ExploreShopResponse> shops, boolean hasMore) {
}
