package com.ucamp.app.domain.schedule.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.ucamp.app.domain.schedule.dto.request.BusinessHourSaveRequest;
import com.ucamp.app.domain.schedule.dto.response.BusinessHourClosingImpactResponse;
import com.ucamp.app.domain.schedule.dto.response.BusinessHourResponse;
import com.ucamp.app.domain.schedule.entity.BusinessDay;
import com.ucamp.app.domain.schedule.service.ShopBusinessHourService;

/**
 * 매장 요일별 영업시간 API. 매장은 로그인한 매장 계정으로 정해진다.
 */
@Tag(name = "영업시간", description = "매장 요일별 영업시간 (매주 반복)")
@RestController
@RequestMapping("/api/v1/business-hours")
public class ShopBusinessHourController {

    private final ShopBusinessHourService shopBusinessHourService;

    public ShopBusinessHourController(ShopBusinessHourService shopBusinessHourService) {
        this.shopBusinessHourService = shopBusinessHourService;
    }

    @Operation(summary = "영업시간 조회", description = "월~일 7개. 저장하지 않은 요일은 휴무로 응답")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BusinessHourResponse>>> getBusinessHours(@LoginUser AuthUser loginUser) {
        return ResponseEntity.ok(ApiResponse.success(shopBusinessHourService.getBusinessHours(loginUser)));
    }

    @Operation(summary = "휴무 전환 영향 조회", description = "요일을 휴무로 바꾸기 전에 그 요일의 앞으로의 예약과 시간대 열기를 조회")
    @GetMapping("/closing-impact")
    public ResponseEntity<ApiResponse<BusinessHourClosingImpactResponse>> getClosingImpact(
            @LoginUser AuthUser loginUser, @RequestParam List<BusinessDay> days) {
        return ResponseEntity.ok(ApiResponse.success(shopBusinessHourService.getClosingImpact(loginUser, days)));
    }

    @Operation(summary = "영업시간 저장",
            description = "월~일 7개를 한 번에 저장. 30분 단위, 자정 넘김 불가. 휴무로 바꾼 요일의 예약은 유지하고, "
                    + "영업하는 요일의 예약이 영업시간 밖으로 밀려나면 409")
    @PutMapping
    public ResponseEntity<ApiResponse<List<BusinessHourResponse>>> updateBusinessHours(
            @LoginUser AuthUser loginUser,
            @Valid @RequestBody BusinessHourSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shopBusinessHourService.updateBusinessHours(loginUser, request)));
    }
}
