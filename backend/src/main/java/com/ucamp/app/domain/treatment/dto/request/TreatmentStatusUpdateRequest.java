package com.ucamp.app.domain.treatment.dto.request;

import jakarta.validation.constraints.NotNull;

import com.ucamp.app.domain.treatment.entity.TreatmentStatus;

public record TreatmentStatusUpdateRequest(@NotNull TreatmentStatus status) {
}
