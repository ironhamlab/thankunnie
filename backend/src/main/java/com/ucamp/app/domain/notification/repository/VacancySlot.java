package com.ucamp.app.domain.notification.repository;

import java.time.LocalDateTime;

/**
 * 조회해서 가져온 빈자리 하나. 테이블 행이 아니라 결과만 담는다.
 *
 * @param exceptionId   OPEN 행 번호. 재공개는 영업시간만으로도 열려 있으면 null
 * @param shopId        매장 번호
 * @param reservationId 취소 재공개면 취소된 예약 번호, 임박·초임박이면 null
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
