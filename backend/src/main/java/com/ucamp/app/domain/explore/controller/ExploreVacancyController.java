package com.ucamp.app.domain.explore.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.ucamp.app.domain.explore.dto.response.ExploreVacancyDetailResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreVacancyResponse;
import com.ucamp.app.domain.explore.service.ExploreVacancyDetailService;
import com.ucamp.app.domain.explore.service.ExploreVacancyService;
import com.ucamp.app.domain.treatment.entity.TreatmentType;
import com.ucamp.app.domain.vacancy.policy.PriceTier;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 고객 빈자리 탐색 API (C-01 목록, C-02 지도 매장 요약, C-03 상세). 비로그인 조회 가능. 계약: docs/api/C-01-vacancy-search.md
 */
@Tag(name = "빈자리 탐색", description = "홈·검색·지도 공용 빈자리 목록과 지도 마커용 매장 요약")
@RestController
@RequestMapping("/api/v1/explore")
public class ExploreVacancyController {

    private final ExploreVacancyService exploreVacancyService;
    private final ExploreVacancyDetailService exploreVacancyDetailService;

    public ExploreVacancyController(ExploreVacancyService exploreVacancyService,
            ExploreVacancyDetailService exploreVacancyDetailService) {
        this.exploreVacancyService = exploreVacancyService;
        this.exploreVacancyDetailService = exploreVacancyDetailService;
    }

    @Operation(summary = "빈자리 목록",
            description = "매장이 연 시간대 안에서 지금 예약할 수 있는 시술 × 시작 시각. 날짜를 생략하면 오늘부터 7일."
                    + " 지도 범위(minLat·maxLat·minLng·maxLng)는 네 값을 함께 보낸다")
    @GetMapping("/vacancies")
    public ResponseEntity<ApiResponse<PageResponse<ExploreVacancyResponse>>> getVacancies(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "regionCode", required = false) List<String> regionCodes,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) TreatmentType treatmentType,
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
                treatmentType, timeOfDay, closingWithinHours, priceTier, keyword, minLat, maxLat, minLng, maxLng);
        return ResponseEntity.ok(ApiResponse.success(
                exploreVacancyService.search(new ExploreVacancySearchRequest(filter, sort, page, size))));
    }

    @Operation(summary = "임박 빈자리",
            description = "메인 화면용. 지금부터 8시간 안에 시작하는 일반 시술 빈자리를 매장마다 가장 빠른 1건씩,"
                    + " 빠른 시작순으로 최대 10개. 매장이 시간대를 열지 않아도 영업시간 안 빈 시각이면 나온다")
    @GetMapping("/vacancies/imminent")
    public ResponseEntity<ApiResponse<List<ExploreVacancyResponse>>> getImminentVacancies() {
        return ResponseEntity.ok(ApiResponse.success(exploreVacancyService.searchImminent()));
    }

    @Operation(summary = "빈자리 상세",
            description = "시술 × 시작 시각의 예약 가능 여부·가격·시술/매장 안내. 조회 시점에 다시 계산하며,"
                    + " 지났거나 마감된 시각도 200 으로 bookable=false 를 돌려준다")
    @GetMapping("/vacancies/{treatmentId}")
    public ResponseEntity<ApiResponse<ExploreVacancyDetailResponse>> getVacancyDetail(
            @PathVariable Long treatmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt) {
        return ResponseEntity.ok(ApiResponse.success(exploreVacancyDetailService.getDetail(treatmentId, startsAt)));
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
            @RequestParam(required = false) TreatmentType treatmentType,
            @RequestParam(required = false) ExploreTimeOfDay timeOfDay,
            @RequestParam(required = false) Integer closingWithinHours,
            @RequestParam(required = false) PriceTier priceTier,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minLat,
            @RequestParam(required = false) BigDecimal maxLat,
            @RequestParam(required = false) BigDecimal minLng,
            @RequestParam(required = false) BigDecimal maxLng) {
        ExploreVacancyFilter filter = new ExploreVacancyFilter(startDate, endDate, regionCodes, categoryId, null,
                treatmentType, timeOfDay, closingWithinHours, priceTier, keyword, minLat, maxLat, minLng, maxLng);
        return ResponseEntity.ok(ApiResponse.success(exploreVacancyService.searchShops(filter)));
    }
}
