package com.ucamp.app.domain.treatment.entity;

/** DELETED는 예약 이력 보존을 위해 행을 남기고 관리·고객 화면에서 제외하는 소프트 삭제 상태다. */
public enum TreatmentStatus {
    ACTIVE, INACTIVE, DELETED
}
