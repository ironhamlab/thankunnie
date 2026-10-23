package com.ucamp.app.domain.shop.controller;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.shop.dto.request.ShopSaveRequest;
import com.ucamp.app.domain.shop.dto.response.ShopDetailResponse;
import com.ucamp.app.domain.shop.service.ShopService;

/** 매장 관리 API. */
@Tag(name = "Shop", description = "본인 매장 관리")
@RestController
@RequestMapping("/api/v1/shops")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @Operation(summary = "본인 매장 정보 등록")
    @PostMapping
    public ResponseEntity<ApiResponse<ShopDetailResponse>> createShop(@LoginUser AuthUser user,
            @Valid @RequestBody ShopSaveRequest request) {
        ShopDetailResponse response = shopService.createShop(user, request);
        return ResponseEntity.created(URI.create("/api/v1/shops/" + response.shopId()))
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "본인 매장 정보 조회")
    @GetMapping("/{shopId}")
    public ResponseEntity<ApiResponse<ShopDetailResponse>> getShop(@LoginUser AuthUser user,
            @PathVariable Long shopId) {
        return ResponseEntity.ok(ApiResponse.success(shopService.getShop(user, shopId)));
    }

    @Operation(summary = "본인 매장 정보 전체 수정")
    @PutMapping("/{shopId}")
    public ResponseEntity<ApiResponse<ShopDetailResponse>> updateShop(@LoginUser AuthUser user,
            @PathVariable Long shopId, @Valid @RequestBody ShopSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(shopService.updateShop(user, shopId, request)));
    }
}
