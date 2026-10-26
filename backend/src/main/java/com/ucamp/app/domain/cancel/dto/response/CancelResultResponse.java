package com.ucamp.app.domain.cancel.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.cancel.entity.CancelStatus;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.cancel.entity.ReservationCancel;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 취소 실행 결과. 취소 결과 화면(E-F3)에서 완료(COMPLETED)와 확인 필요(UNKNOWN)를 구분해 안내한다.
 * 결제 취소가 확실히 실패하면 이 응답 대신 결제 에러 코드(PAYMENT_00x)로 응답하며, 예약은 확정 상태로 돌아간다.
 *
 * @param cancelStatus      COMPLETED: 환불 완료, UNKNOWN: 환불 결과 확인 중 (예약은 CANCEL_PENDING 으로 시간 차단)
 * @param reservationStatus 반영 후 예약 상태
 * @param graceApplied      무료 취소로 기본 정책보다 더 환불받았는지
 * @param completedAt       환불 완료 시각, 완료 전이면 null
 */
public record CancelResultResponse(
        Long cancelId,
        Long reservationId,
        CancelType cancelType,
        CancelStatus cancelStatus,
        ReservationStatus reservationStatus,
        int refundAmount,
        boolean graceApplied,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt) {

    public static CancelResultResponse of(ReservationCancel cancel, ReservationStatus reservationStatus) {
        boolean graceApplied = cancel.getCancelType() == CancelType.CUSTOMER
                && cancel.getRefundAmount() > cancel.getPolicyRefundAmount();
        return new CancelResultResponse(
                cancel.getId(),
                cancel.getReservationId(),
                cancel.getCancelType(),
                cancel.getStatus(),
                reservationStatus,
                cancel.getRefundAmount(),
                graceApplied,
                TimeUtils.toOffset(cancel.getRequestedAt()),
                TimeUtils.toOffset(cancel.getCompletedAt()));
    }
}
