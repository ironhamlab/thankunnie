package com.ucamp.app.domain.cancel.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.cancel.dto.request.CustomerCancelRequest;
import com.ucamp.app.domain.cancel.dto.response.CancelResultResponse;
import com.ucamp.app.domain.payment.service.PaymentService;

/**
 * 취소 실행 흐름 (E-05·E-06). 트랜잭션을 걸지 않고 단계별 트랜잭션을 순서대로 호출한다.
 * 1) 취소 접수 커밋(예약 CANCEL_PENDING) → 2) 트랜잭션 밖에서 결제 취소 → 3) 결과 반영 커밋.
 * PG 호출 중 DB 잠금을 잡지 않고, 결제 취소 중에도 시간이 막혀 있게 하려는 순서다.
 */
@Slf4j
@Service
public class CancelService {

    private static final String CUSTOMER_CANCEL_REASON = "고객 직접 취소";

    private final CancelRequestService cancelRequestService;
    private final CancelResultService cancelResultService;
    private final PaymentService paymentService;
    private final Clock clock;

    public CancelService(CancelRequestService cancelRequestService, CancelResultService cancelResultService,
            PaymentService paymentService, Clock clock) {
        this.cancelRequestService = cancelRequestService;
        this.cancelResultService = cancelResultService;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    public CancelResultResponse cancelByCustomer(AuthUser requester, Long reservationId,
            CustomerCancelRequest request) {
        AcceptedCancel accepted = cancelRequestService.acceptCustomerCancel(requester, reservationId,
                request.expectedRefundAmount(), LocalDateTime.now(clock));
        return settle(accepted, CUSTOMER_CANCEL_REASON);
    }

    /**
     * 결제 취소를 호출하고 결과를 반영한다.
     * 확실한 실패(PAYMENT_005 외 업무 예외)면 예약을 확정으로 되돌리고 예외를 그대로 응답한다.
     * 결과 불명(PAYMENT_005)이나 예상 못 한 예외면 실제로 환불됐을 수 있으므로 취소 처리 중으로 둔다.
     */
    private CancelResultResponse settle(AcceptedCancel accepted, String reason) {
        // 0원 환불은 결제 쪽에서 거절(PAYMENT_004)하므로 호출하지 않고 바로 완료한다
        if (accepted.refundAmount() == 0) {
            return cancelResultService.complete(accepted.cancelId(), LocalDateTime.now(clock));
        }
        try {
            paymentService.refund(accepted.depositPaymentId(), accepted.refundAmount(), reason);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PAYMENT_REFUND_UNCERTAIN) {
                log.warn("결제 취소 결과 불명, 취소 처리 중 유지: cancelId={}, reservationId={}",
                        accepted.cancelId(), accepted.reservationId());
                return cancelResultService.markUnknown(accepted.cancelId());
            }
            cancelResultService.fail(accepted.cancelId(), LocalDateTime.now(clock));
            throw e;
        } catch (RuntimeException e) {
            log.error("결제 취소 중 예상 못 한 오류, 취소 처리 중 유지: cancelId={}, reservationId={}",
                    accepted.cancelId(), accepted.reservationId(), e);
            return cancelResultService.markUnknown(accepted.cancelId());
        }
        return cancelResultService.complete(accepted.cancelId(), LocalDateTime.now(clock));
    }
}
