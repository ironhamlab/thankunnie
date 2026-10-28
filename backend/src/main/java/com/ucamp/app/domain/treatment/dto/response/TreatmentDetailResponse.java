package com.ucamp.app.domain.treatment.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;
import com.ucamp.app.domain.treatment.entity.TreatmentType;

public record TreatmentDetailResponse(
        Long treatmentId, Long shopId, String name, String description,
        Integer regularPrice, Integer price, Integer lastMinutePrice,
        Integer durationMinutes, TreatmentStatus status,
        TreatmentType treatmentType, String modelCondition, String photoNotice, String imageUrl,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public static TreatmentDetailResponse from(Treatment treatment) {
        return new TreatmentDetailResponse(
                treatment.getTreatmentId(), treatment.getShop().getShopId(),
                treatment.getName(), treatment.getDescription(), treatment.getRegularPrice(),
                treatment.getPrice(), treatment.getLastMinutePrice(),
                treatment.getDurationMinutes(), treatment.getStatus(),
                treatment.getTreatmentType(),
                treatment.getModelCondition(), treatment.getPhotoNotice(), treatment.getImageUrl(),
                TimeUtils.toOffset(treatment.getCreatedAt()), TimeUtils.toOffset(treatment.getUpdatedAt()));
    }
}
