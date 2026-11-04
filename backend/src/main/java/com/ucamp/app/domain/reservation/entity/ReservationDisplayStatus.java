package com.ucamp.app.domain.reservation.entity;

import java.time.LocalDateTime;

/**
 * 고객 화면에 보여줄 예약 상태. 저장하지 않고 조회할 때 계산한다.
 * 저장 상태가 CONFIRMED 인 예약을 시술 종료 시각과 노쇼 표시로 나누고, 나머지는 저장 상태 그대로 둔다.
 */
public enum ReservationDisplayStatus {
    HOLD,
    /** 확정됐고 시술 종료 시각 전이다 */
    CONFIRMED,
    /** 확정됐고 시술 종료 시각이 지났다 */
    COMPLETED,
    /** 매장이 노쇼로 표시했다 */
    NO_SHOW,
    CANCEL_PENDING,
    CANCELLED,
    EXPIRED;

    public static ReservationDisplayStatus of(ReservationStatus status, LocalDateTime endsAt, boolean noShow,
            LocalDateTime now) {
        return switch (status) {
            case HOLD -> HOLD;
            case CONFIRMED -> confirmed(endsAt, noShow, now);
            case CANCEL_PENDING -> CANCEL_PENDING;
            case CANCELLED -> CANCELLED;
            case EXPIRED -> EXPIRED;
        };
    }

    private static ReservationDisplayStatus confirmed(LocalDateTime endsAt, boolean noShow, LocalDateTime now) {
        if (noShow) {
            return NO_SHOW;
        }
        return endsAt.isAfter(now) ? CONFIRMED : COMPLETED;
    }
}
