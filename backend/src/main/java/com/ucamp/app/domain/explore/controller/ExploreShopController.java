package com.ucamp.app.domain.explore.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreShopDetailResponse;
import com.ucamp.app.domain.explore.service.ExploreShopDetailService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "빈자리 탐색", description = "홈·검색·지도 공용 빈자리 목록과 지도 마커용 매장 요약")
@RestController
@RequestMapping("/api/v1/explore")
public class ExploreShopController {

    private final ExploreShopDetailService exploreShopDetailService;

    public ExploreShopController(ExploreShopDetailService exploreShopDetailService) {
        this.exploreShopDetailService = exploreShopDetailService;
    }

    @Operation(summary = "매장 상세",
            description = "로그인 없이 매장 안내·월~일 영업시간·판매 중인 시술을 조회한다. 매장이 없거나 비활성이면 404")
    @GetMapping("/shops/{shopId}")
    public ResponseEntity<ApiResponse<ExploreShopDetailResponse>> getShop(@PathVariable Long shopId) {
        return ResponseEntity.ok(ApiResponse.success(exploreShopDetailService.getShop(shopId)));
    }
}
