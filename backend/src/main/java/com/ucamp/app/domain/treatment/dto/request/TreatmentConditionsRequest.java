package com.ucamp.app.domain.treatment.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.ucamp.app.domain.treatment.entity.TreatmentType;

/** 시술 목적과 모델 모집 조건. 참가비는 시술 판매가와 별도로 저장한다. */
public record TreatmentConditionsRequest(
        @NotNull TreatmentType treatmentType,
        @PositiveOrZero Integer participationFee,
        @Size(max = 2000) String modelCondition,
        @Size(max = 1000) String photoNotice) {

    @AssertTrue(message = "모델 모집은 0원 이상의 100원 단위 참가비, 참여 조건, 촬영 안내가 필수입니다.")
    public boolean isModelConditionsValid() {
        if (treatmentType != TreatmentType.MODEL) {
            return true;
        }
        return participationFee != null && participationFee >= 0 && participationFee % 100 == 0
                && modelCondition != null && !modelCondition.isBlank()
                && photoNotice != null && !photoNotice.isBlank();
    }

    @AssertTrue(message = "일반 시술에는 모델 참가비와 참여 조건, 촬영 안내를 입력할 수 없습니다.")
    public boolean isNormalConditionsValid() {
        return treatmentType != TreatmentType.NORMAL
                || participationFee == null && (modelCondition == null || modelCondition.isBlank())
                && (photoNotice == null || photoNotice.isBlank());
    }
}
