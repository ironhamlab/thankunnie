package com.ucamp.app.domain.shop.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.shop.dto.request.ShopUpdateRequest;
import com.ucamp.app.domain.shop.dto.response.ShopDetailResponse;
import com.ucamp.app.domain.shop.service.ShopService;

/** 사장님 본인 매장 관리 API. */
@Tag(name = "Shop", description = "매장 관리")
@RestController
@RequestMapping("/api/v1/shops")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @Operation(summary = "본인 매장 정보 조회")
    @GetMapping("/{shopId}")
    public ApiResponse<ShopDetailResponse> getShop(@LoginUser AuthUser user, @PathVariable Long shopId) {
        return ApiResponse.success(shopService.getShop(user, shopId));
    }

    @Operation(summary = "본인 매장 정보·주소·좌표 저장 및 수정")
    @PutMapping("/{shopId}")
    public ApiResponse<ShopDetailResponse> updateShop(
            @LoginUser AuthUser user, @PathVariable Long shopId,
            @Valid @RequestBody ShopUpdateRequest request) {
        return ApiResponse.success(shopService.updateShop(user, shopId, request));
    }
}
