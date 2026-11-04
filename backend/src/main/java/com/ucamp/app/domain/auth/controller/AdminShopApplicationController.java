package com.ucamp.app.domain.auth.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.ucamp.app.domain.auth.dto.request.ShopApplicationRejectRequest;
import com.ucamp.app.domain.auth.dto.response.ShopApplicationResponse;
import com.ucamp.app.domain.auth.entity.ShopAccountStatus;
import com.ucamp.app.domain.auth.service.AdminShopApplicationService;

@Tag(name = "관리자 매장 가입 신청")
@RestController
@RequestMapping("/api/v1/admin/shop-applications")
public class AdminShopApplicationController {

    private final AdminShopApplicationService adminShopApplicationService;

    public AdminShopApplicationController(AdminShopApplicationService adminShopApplicationService) {
        this.adminShopApplicationService = adminShopApplicationService;
    }

    @Operation(summary = "가입 신청 목록", description = "상태별 신청 목록. 최신 신청 순, 기본은 승인 대기. keyword 로 매장명·아이디·연락처를 찾는다")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ShopApplicationResponse>>> getApplications(
            @LoginUser AuthUser loginUser,
            @RequestParam(defaultValue = "PENDING") ShopAccountStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminShopApplicationService.getApplications(loginUser, status, keyword, page, size)));
    }

    @Operation(summary = "가입 승인", description = "승인 대기 신청을 승인해 매장을 노출 상태로 바꾼다")
    @PatchMapping("/{shopId}/approve")
    public ResponseEntity<ApiResponse<ShopApplicationResponse>> approve(
            @LoginUser AuthUser loginUser, @PathVariable Long shopId) {
        return ResponseEntity.ok(ApiResponse.success(adminShopApplicationService.approve(loginUser, shopId)));
    }

    @Operation(summary = "가입 거절", description = "승인 대기 신청을 거절하고 사유를 남긴다")
    @PatchMapping("/{shopId}/reject")
    public ResponseEntity<ApiResponse<ShopApplicationResponse>> reject(
            @LoginUser AuthUser loginUser, @PathVariable Long shopId,
            @Valid @RequestBody ShopApplicationRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminShopApplicationService.reject(loginUser, shopId, request.reason())));
    }

    @Operation(summary = "승인 취소", description = "잘못 승인한 매장을 거절 상태로 바꾼다. 진행 중이거나 앞으로 있을 예약이 있으면 막는다")
    @PatchMapping("/{shopId}/cancel-approval")
    public ResponseEntity<ApiResponse<ShopApplicationResponse>> cancelApproval(
            @LoginUser AuthUser loginUser, @PathVariable Long shopId,
            @Valid @RequestBody ShopApplicationRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminShopApplicationService.cancelApproval(loginUser, shopId, request.reason())));
    }

    @Operation(summary = "거절 취소", description = "잘못 거절한 신청을 승인 대기로 되돌린다")
    @PatchMapping("/{shopId}/cancel-rejection")
    public ResponseEntity<ApiResponse<ShopApplicationResponse>> cancelRejection(
            @LoginUser AuthUser loginUser, @PathVariable Long shopId) {
        return ResponseEntity.ok(ApiResponse.success(
                adminShopApplicationService.cancelRejection(loginUser, shopId)));
    }
}
