package com.ucamp.app.domain.treatment.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.ucamp.app.domain.treatment.dto.request.TreatmentSaveRequest;
import com.ucamp.app.domain.treatment.dto.request.TreatmentStatusUpdateRequest;
import com.ucamp.app.domain.treatment.dto.response.TreatmentCategoryResponse;
import com.ucamp.app.domain.treatment.dto.response.TreatmentDetailResponse;
import com.ucamp.app.domain.treatment.service.TreatmentService;

@Tag(name = "Treatment", description = "사장님 본인 매장 시술 메뉴 관리")
@RestController
@RequestMapping("/api/v1/shops/{shopId}/treatments")
public class TreatmentController {

    private final TreatmentService treatmentService;

    public TreatmentController(TreatmentService treatmentService) {
        this.treatmentService = treatmentService;
    }

    @Operation(summary = "시술 목록 조회 (활성·비활성 포함)")
    @GetMapping
    public ApiResponse<List<TreatmentDetailResponse>> getTreatments(
            @LoginUser AuthUser user, @PathVariable Long shopId) {
        return ApiResponse.success(treatmentService.getTreatments(user, shopId));
    }

    /** 신규 빈자리 등록 화면에 제공할 활성 시술 목록. */
    @Operation(summary = "신규 빈자리 선택용 활성 시술 목록 조회")
    @GetMapping("/selectable")
    public ApiResponse<List<TreatmentDetailResponse>> getSelectableTreatments(
            @LoginUser AuthUser user, @PathVariable Long shopId) {
        return ApiResponse.success(treatmentService.getSelectableTreatments(user, shopId));
    }

    @Operation(summary = "선택 가능한 시술 카테고리 조회")
    @GetMapping("/categories")
    public ApiResponse<List<TreatmentCategoryResponse>> getCategories(
            @LoginUser AuthUser user, @PathVariable Long shopId) {
        return ApiResponse.success(treatmentService.getCategories(user, shopId));
    }

    @Operation(summary = "시술 단건 조회")
    @GetMapping("/{treatmentId}")
    public ApiResponse<TreatmentDetailResponse> getTreatment(
            @LoginUser AuthUser user, @PathVariable Long shopId, @PathVariable Long treatmentId) {
        return ApiResponse.success(treatmentService.getTreatment(user, shopId, treatmentId));
    }

    @Operation(summary = "시술 메뉴 등록")
    @PostMapping
    public ResponseEntity<ApiResponse<TreatmentDetailResponse>> createTreatment(
            @LoginUser AuthUser user, @PathVariable Long shopId,
            @Valid @RequestBody TreatmentSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(treatmentService.createTreatment(user, shopId, request)));
    }

    @Operation(summary = "시술 메뉴 기본 정보 수정")
    @PutMapping("/{treatmentId}")
    public ApiResponse<TreatmentDetailResponse> updateTreatment(
            @LoginUser AuthUser user, @PathVariable Long shopId, @PathVariable Long treatmentId,
            @Valid @RequestBody TreatmentSaveRequest request) {
        return ApiResponse.success(treatmentService.updateTreatment(user, shopId, treatmentId, request));
    }

    @Operation(summary = "시술 활성·비활성 상태 변경")
    @PatchMapping("/{treatmentId}/status")
    public ApiResponse<TreatmentDetailResponse> updateStatus(
            @LoginUser AuthUser user, @PathVariable Long shopId, @PathVariable Long treatmentId,
            @Valid @RequestBody TreatmentStatusUpdateRequest request) {
        return ApiResponse.success(treatmentService.updateStatus(user, shopId, treatmentId, request));
    }
}
