package com.ucamp.app.domain.reservation.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;

/**
 * 예약 대기열의 내 순서. 대기 화면은 이 응답을 주기적으로 다시 받아 순서를 갱신한다.
 *
 * @param status        내 상태
 * @param aheadCount    내 앞에 있는 사람 수. 결제 중인 사람도 한 명으로 센다
 * @param paying        다른 사람이 이 빈자리를 점유하고 결제 중인지
 * @param turnExpiresAt 내 차례(MY_TURN)가 끝나는 일시. 그 밖의 상태에서는 null
 * @param reservationId 내 점유 예약 ID (HOLDING). 그 밖의 상태에서는 null
 * @param serverTime    응답을 만든 서버 시각. 화면이 기기 시각 차이를 보정해 남은 시간을 센다
 */
public record ReservationQueueResponse(
        ReservationQueueStatus status,
        int aheadCount,
        boolean paying,
        OffsetDateTime turnExpiresAt,
        Long reservationId,
        OffsetDateTime serverTime) {

    public static ReservationQueueResponse waiting(int aheadCount, boolean paying, LocalDateTime now) {
        return new ReservationQueueResponse(ReservationQueueStatus.WAITING, aheadCount, paying, null, null,
                TimeUtils.toOffset(now));
    }

    public static ReservationQueueResponse myTurn(LocalDateTime turnExpiresAt, LocalDateTime now) {
        return new ReservationQueueResponse(ReservationQueueStatus.MY_TURN, 0, false,
                TimeUtils.toOffset(turnExpiresAt), null, TimeUtils.toOffset(now));
    }

    public static ReservationQueueResponse holding(Long reservationId, LocalDateTime now) {
        return new ReservationQueueResponse(ReservationQueueStatus.HOLDING, 0, false, null, reservationId,
                TimeUtils.toOffset(now));
    }

    public static ReservationQueueResponse closed(LocalDateTime now) {
        return new ReservationQueueResponse(ReservationQueueStatus.CLOSED, 0, false, null, null,
                TimeUtils.toOffset(now));
    }
}
