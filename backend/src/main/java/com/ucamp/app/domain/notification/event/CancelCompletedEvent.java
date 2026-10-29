package com.ucamp.app.domain.notification.event;

import com.ucamp.app.domain.cancel.entity.CancelType;

/**
 * 예약 취소가 완료됐음을 알리는 이벤트. 취소 결과 반영(CancelResultService.complete)에서 발행한다.
 * 고객 직접 취소·사장님 취소·0원 환불 취소 모두 여기를 지난다.
 *
 * @param reservationId 취소된 예약 ID
 * @param cancelType    누가 취소했는지. 문자 문구를 고를 때 쓴다
 */
public record CancelCompletedEvent(Long reservationId, CancelType cancelType) {
}
