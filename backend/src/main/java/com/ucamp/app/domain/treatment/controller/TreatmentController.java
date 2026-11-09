package com.ucamp.app.domain.treatment.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.ucamp.app.domain.treatment.dto.request.TreatmentSaveRequest;
import com.ucamp.app.domain.treatment.dto.request.TreatmentStatusUpdateRequest;
import com.ucamp.app.domain.treatment.dto.response.TreatmentDetailResponse;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;
import com.ucamp.app.domain.treatment.service.TreatmentService;

@Tag(name = "Treatment", description = "본인 매장 시술 메뉴 관리")
@RestController
@RequestMapping("/api/v1/shops/{shopId}/treatments")
public class TreatmentController {

    private final TreatmentService treatmentService;

    public TreatmentController(TreatmentService treatmentService) {
        this.treatmentService = treatmentService;
    }

    /** 상태 조건을 생략하면 관리용 전체 목록, ACTIVE를 지정하면 신규 선택 후보를 반환한다. */
    @Operation(summary = "시술 목록 조회 (상태 생략 시 전체, 신규 빈자리 선택 시 status=ACTIVE)")
    @GetMapping
    public ApiResponse<List<TreatmentDetailResponse>> getTreatments(
            @LoginUser AuthUser user, @PathVariable Long shopId,
            @RequestParam(required = false) TreatmentStatus status) {
        return ApiResponse.success(treatmentService.getTreatments(user, shopId, status));
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

    @Operation(summary = "시술 메뉴 삭제", description = "예약 이력 보존을 위해 DELETED 상태로 바꾸고 목록에서 제외한다.")
    @DeleteMapping("/{treatmentId}")
    public ResponseEntity<Void> deleteTreatment(
            @LoginUser AuthUser user, @PathVariable Long shopId, @PathVariable Long treatmentId) {
        treatmentService.deleteTreatment(user, shopId, treatmentId);
        return ResponseEntity.noContent().build();
    }
}
