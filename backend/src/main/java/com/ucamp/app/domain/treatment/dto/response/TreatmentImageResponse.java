package com.ucamp.app.domain.treatment.dto.response;

/** 시술 대표 이미지 저장 결과. */
public record TreatmentImageResponse(Long treatmentId, String imageUrl) {
}
