package com.ucamp.app.domain.treatment.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ucamp.app.domain.treatment.entity.TreatmentType;

/** 시술 목적과 모델 모집 조건. */
public record TreatmentConditionsRequest(
        @NotNull TreatmentType treatmentType,
        @Size(max = 2000) String modelCondition,
        @Size(max = 1000) String photoNotice) {

    @AssertTrue(message = "모델 모집은 참여 조건과 촬영 안내가 필수입니다.")
    public boolean isModelConditionsValid() {
        if (treatmentType != TreatmentType.MODEL) {
            return true;
        }
        return modelCondition != null && !modelCondition.isBlank()
                && photoNotice != null && !photoNotice.isBlank();
    }

    @AssertTrue(message = "일반 시술에는 모델 참여 조건과 촬영 안내를 입력할 수 없습니다.")
    public boolean isNormalConditionsValid() {
        return treatmentType != TreatmentType.NORMAL
                || (modelCondition == null || modelCondition.isBlank())
                && (photoNotice == null || photoNotice.isBlank());
    }
}
