package com.ucamp.app.domain.treatment.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.response.ApiResponse;
import com.ucamp.app.domain.shop.dto.response.ShopImageContentResponse;
import com.ucamp.app.domain.treatment.dto.response.TreatmentImageResponse;
import com.ucamp.app.domain.treatment.service.TreatmentImageService;

/** 시술 대표 이미지 업로드·교체·삭제 및 이미지 조회 API. */
@Tag(name = "Treatment", description = "시술 관리")
@RestController
@RequestMapping("/api/v1/shops")
public class TreatmentImageController {

    private final TreatmentImageService service;

    public TreatmentImageController(TreatmentImageService service) {
        this.service = service;
    }

    @Operation(summary = "본인 시술 대표 이미지 업로드·교체", description = "file: JPEG·PNG, 최대 5MB·1,600만 화소")
    @PutMapping(value = "/me/treatments/{treatmentId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TreatmentImageResponse> uploadImage(@LoginUser AuthUser user, @PathVariable Long treatmentId,
            @RequestParam(value = "file", required = false) List<MultipartFile> files) {
        if (files != null && files.size() != 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대표 이미지는 한 번에 한 개만 업로드해 주세요.");
        }
        return ApiResponse.success(service.uploadImage(user, user.shopId(), treatmentId,
                files == null ? null : files.getFirst()));
    }

    @Operation(summary = "본인 시술 대표 이미지 삭제")
    @DeleteMapping("/me/treatments/{treatmentId}/image")
    public ResponseEntity<Void> deleteImage(@LoginUser AuthUser user, @PathVariable Long treatmentId) {
        service.deleteImage(user, user.shopId(), treatmentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "현재 시술 대표 이미지 공개 조회")
    @GetMapping("/{shopId}/treatments/{treatmentId}/images/{fileName}")
    public ResponseEntity<byte[]> getImage(@PathVariable Long shopId, @PathVariable Long treatmentId,
            @PathVariable String fileName) {
        ShopImageContentResponse image = service.getImage(shopId, treatmentId, fileName);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }
}
