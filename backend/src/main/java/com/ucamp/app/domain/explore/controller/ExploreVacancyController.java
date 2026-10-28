package com.ucamp.app.domain.explore.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.domain.explore.dto.request.ExploreTimeOfDay;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancyFilter;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancySearchRequest;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancySort;
import com.ucamp.app.domain.explore.dto.response.ExploreShopMapResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreVacancyResponse;
import com.ucamp.app.domain.explore.service.ExploreVacancyService;
import com.ucamp.app.domain.vacancy.policy.PriceTier;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 고객 빈자리 탐색 API (C-01 목록, C-02 지도 매장 요약). 비로그인 조회 가능. 계약: docs/api/C-01-vacancy-search.md
 */
@Tag(name = "빈자리 탐색", description = "홈·검색·지도 공용 빈자리 목록과 지도 마커용 매장 요약")
@RestController
@RequestMapping("/api/v1/explore")
public class ExploreVacancyController {

    private final ExploreVacancyService exploreVacancyService;

    public ExploreVacancyController(ExploreVacancyService exploreVacancyService) {
        this.exploreVacancyService = exploreVacancyService;
    }

    @Operation(summary = "빈자리 목록",
            description = "사장님이 연 시간대 안에서 지금 예약할 수 있는 시술 × 시작 시각. 날짜를 생략하면 오늘부터 7일."
                    + " 지도 범위(minLat·maxLat·minLng·maxLng)는 네 값을 함께 보낸다")
    @GetMapping("/vacancies")
    public ResponseEntity<ApiResponse<PageResponse<ExploreVacancyResponse>>> getVacancies(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "regionCode", required = false) List<String> regionCodes,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) ExploreTimeOfDay timeOfDay,
            @RequestParam(required = false) Integer closingWithinHours,
            @RequestParam(required = false) PriceTier priceTier,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minLat,
            @RequestParam(required = false) BigDecimal maxLat,
            @RequestParam(required = false) BigDecimal minLng,
            @RequestParam(required = false) BigDecimal maxLng,
            @RequestParam(defaultValue = "START_ASC") ExploreVacancySort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ExploreVacancyFilter filter = new ExploreVacancyFilter(startDate, endDate, regionCodes, categoryId, shopId,
                timeOfDay, closingWithinHours, priceTier, keyword, minLat, maxLat, minLng, maxLng);
        return ResponseEntity.ok(ApiResponse.success(
                exploreVacancyService.search(new ExploreVacancySearchRequest(filter, sort, page, size))));
    }

    @Operation(summary = "지도 매장 요약",
            description = "빈자리가 있는 매장마다 마커 1개용 요약(최저 판매가·최대 할인율·빈자리 수·가장 빠른 시작)."
                    + " 필터는 빈자리 목록과 같고, 가장 빠른 시작 순으로 최대 200개")
    @GetMapping("/shops")
    public ResponseEntity<ApiResponse<ExploreShopMapResponse>> getShops(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "regionCode", required = false) List<String> regionCodes,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ExploreTimeOfDay timeOfDay,
            @RequestParam(required = false) Integer closingWithinHours,
            @RequestParam(required = false) PriceTier priceTier,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minLat,
            @RequestParam(required = false) BigDecimal maxLat,
            @RequestParam(required = false) BigDecimal minLng,
            @RequestParam(required = false) BigDecimal maxLng) {
        ExploreVacancyFilter filter = new ExploreVacancyFilter(startDate, endDate, regionCodes, categoryId, null,
                timeOfDay, closingWithinHours, priceTier, keyword, minLat, maxLat, minLng, maxLng);
        return ResponseEntity.ok(ApiResponse.success(exploreVacancyService.searchShops(filter)));
    }
}
