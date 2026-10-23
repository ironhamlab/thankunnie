package com.ucamp.app.domain.treatment.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;

public record TreatmentDetailResponse(
        Long treatmentId, Long shopId, String name, String description,
        Integer regularPrice, Long categoryId, String categoryName,
        Integer durationMinutes, TreatmentStatus status,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public static TreatmentDetailResponse from(Treatment treatment) {
        return new TreatmentDetailResponse(
                treatment.getTreatmentId(), treatment.getShop().getShopId(),
                treatment.getName(), treatment.getDescription(), treatment.getRegularPrice(),
                treatment.getCategory().getCategoryId(), treatment.getCategory().getName(),
                treatment.getDurationMinutes(), treatment.getStatus(),
                TimeUtils.toOffset(treatment.getCreatedAt()), TimeUtils.toOffset(treatment.getUpdatedAt()));
    }
}
