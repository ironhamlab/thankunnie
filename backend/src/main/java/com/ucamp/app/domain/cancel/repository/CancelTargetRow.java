package com.ucamp.app.domain.cancel.repository;

import java.time.LocalDateTime;

/**
 * 취소 대상 조회(CancelTargetRepository) 네이티브 쿼리 결과 프로젝션. 별칭은 대소문자를 지키도록 큰따옴표로 쓴다.
 */
public interface CancelTargetRow {

    Long getReservationId();

    Long getUserId();

    Long getShopId();

    String getStatus();

    LocalDateTime getStartsAt();

    LocalDateTime getHeldAt();

    Integer getPaidAmount();

    LocalDateTime getApprovedAt();
}
