package com.ucamp.app.domain.cancel.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.domain.cancel.entity.CancelStatus;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.cancel.service.AcceptedCancel;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 사장님 취소 결과. 매장 사유 취소는 문자 안내가 없으므로 고객 연락처를 함께 내려 사장님이 직접 연락하게 한다 (CNL-20, E-F2).
 *
 * @param closed 취소한 시간을 닫았는지. false 면 다시 예약 가능 시간으로 열린다
 */
public record OwnerCancelResponse(
        Long cancelId,
        Long reservationId,
        CancelType cancelType,
        CancelStatus cancelStatus,
        ReservationStatus reservationStatus,
        int refundAmount,
        boolean closed,
        String customerName,
        String customerPhone,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt) {

    public static OwnerCancelResponse of(CancelResultResponse result, AcceptedCancel accepted) {
        return new OwnerCancelResponse(
                result.cancelId(),
                result.reservationId(),
                result.cancelType(),
                result.cancelStatus(),
                result.reservationStatus(),
                result.refundAmount(),
                accepted.closed(),
                accepted.customerName(),
                accepted.customerPhone(),
                result.requestedAt(),
                result.completedAt());
    }
}
