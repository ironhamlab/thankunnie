package com.ucamp.app.domain.notification.repository;

import java.time.LocalDateTime;

/**
 * 알림 대상 빈자리. 예외 일정 OPEN 행(exceptionId)으로 식별한다. 엔티티가 아닌 조회 결과다.
 *
 * @param exceptionId   빈자리인 OPEN 예외 일정 ID
 * @param shopId        매장 ID
 * @param reservationId 취소 재공개면 취소된 예약 ID, 임박이면 null
 * @param startsAt      빈자리 시작 시각
 * @param endsAt        빈자리 종료 시각 (미포함)
 */
public record VacancySlot(
        Long exceptionId,
        Long shopId,
        Long reservationId,
        LocalDateTime startsAt,
        LocalDateTime endsAt) {
}
