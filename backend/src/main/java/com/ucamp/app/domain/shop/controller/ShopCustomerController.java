package com.ucamp.app.domain.shop.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.domain.shop.dto.request.ShopCustomerMemoRequest;
import com.ucamp.app.domain.shop.dto.request.ShopCustomerSegmentFilter;
import com.ucamp.app.domain.shop.dto.request.ShopCustomerSort;
import com.ucamp.app.domain.shop.dto.response.ShopCustomerDetailResponse;
import com.ucamp.app.domain.shop.dto.response.ShopCustomerMemoResponse;
import com.ucamp.app.domain.shop.dto.response.ShopCustomerResponse;
import com.ucamp.app.domain.shop.service.ShopCustomerService;

/**
 * 사장님 고객 관리 API. 매장은 로그인한 사장님의 매장으로 정해진다.
 */
@Tag(name = "사장님 고객 관리", description = "본인 매장 예약 고객 목록·상세·매장 메모")
@RestController
@RequestMapping("/api/v1/owner/customers")
public class ShopCustomerController {

    private final ShopCustomerService shopCustomerService;

    public ShopCustomerController(ShopCustomerService shopCustomerService) {
        this.shopCustomerService = shopCustomerService;
    }

    @Operation(summary = "고객 목록",
            description = "확정 예약이 있는 고객. segment: ALL·NEW(이용 0~1회)·RETURNING(2회 이상), "
                    + "sort: RECENT(최근 이용)·VISITS(이용 횟수)·AMOUNT(누적 금액), keyword: 이름·연락처 일부")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ShopCustomerResponse>>> getCustomers(
            @LoginUser AuthUser loginUser,
            @RequestParam(defaultValue = "ALL") ShopCustomerSegmentFilter segment,
            @RequestParam(defaultValue = "RECENT") ShopCustomerSort sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                shopCustomerService.getCustomers(loginUser, segment, sort, keyword, page, size)));
    }

    @Operation(summary = "고객 상세", description = "요약, 매장 메모, 예약 이력(최근 50건). 이 매장 예약이 없는 고객은 404")
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<ShopCustomerDetailResponse>> getCustomer(
            @LoginUser AuthUser loginUser, @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(shopCustomerService.getCustomer(loginUser, userId)));
    }

    @Operation(summary = "고객 메모 저장", description = "최대 1,000자. 비우면 메모를 지우고 data 는 null")
    @PutMapping("/{userId}/memo")
    public ResponseEntity<ApiResponse<ShopCustomerMemoResponse>> saveMemo(
            @LoginUser AuthUser loginUser, @PathVariable Long userId,
            @Valid @RequestBody ShopCustomerMemoRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shopCustomerService.saveMemo(loginUser, userId, request.memo())));
    }
}
