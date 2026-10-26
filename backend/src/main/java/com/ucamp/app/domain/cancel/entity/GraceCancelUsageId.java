package com.ucamp.app.domain.cancel.entity;

import java.io.Serializable;
import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 무료 취소 사용 기록 복합 키 (회원 ID + 한국 날짜).
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class GraceCancelUsageId implements Serializable {

    private Long userId;
    private LocalDate usedDate;
}
