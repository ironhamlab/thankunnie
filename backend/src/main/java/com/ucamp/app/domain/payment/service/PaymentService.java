package com.ucamp.app.domain.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.payment.client.PortOneClient;
import com.ucamp.app.domain.payment.dto.request.PaymentVerifyRequest;
import com.ucamp.app.domain.payment.dto.response.PaymentCancelResponse;
import com.ucamp.app.domain.payment.dto.response.PaymentVerifyResponse;

@Slf4j
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PortOneClient portOneClient;
    private final PaymentRefundStateService refundStateService;
    private final Clock clock;

    public PaymentService(PortOneClient portOneClient,
                          PaymentRefundStateService refundStateService,
                          Clock clock) {
        this.portOneClient = portOneClient;
        this.refundStateService = refundStateService;
        this.clock = clock;
    }

    // DB 연동 전 단계: 포트원 조회 결과만 반환한다. 이후 주문 금액 비교·저장을 여기에 추가
    public PaymentVerifyResponse verify(PaymentVerifyRequest request) {
        return PaymentVerifyResponse.from(portOneClient.getPayment(request.impUid()));
    }

    /**
     * 예약금결제를 지정 금액만큼 환불한다. 고객이 직접 호출하는 API 는 두지 않고,
     * 예약 취소 도메인이 소유자 확인과 환불 정책 계산을 마친 뒤 호출한다.
     * 이 메서드는 소유자나 환불 정책을 검사하지 않으므로 호출하는 쪽이 책임진다.
     *
     * <p>PG 호출을 DB 트랜잭션 밖에서 하므로 호출한 쪽의 트랜잭션은 잠시 중단되고,
     * 환불 상태 변경은 호출한 쪽의 트랜잭션과 별개로 커밋된다.
     *
     * @param refundAmount 정책으로 계산한 환불 금액 (남은 금액 이하)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentCancelResponse refund(Long depositPaymentId, int refundAmount, String reason) {
        return executeRefund(refundStateService.start(depositPaymentId, refundAmount), reason);
    }

    /**
     * 1) REFUND_PENDING 커밋 → 2) 트랜잭션 밖에서 PG 취소 → 3) 결과 반영.
     * PG 결과가 불확실하면 REFUND_PENDING 을 유지해 중복 환불을 막고, 실제 취소 여부는 보정 작업에서 확인한다.
     */
    private PaymentCancelResponse executeRefund(RefundTarget target, String reason) {
        try {
            portOneClient.cancelPayment(target.impUid(), target.refundAmount(), reason);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PAYMENT_REFUND_UNCERTAIN) {
                log.warn("포트원 환불 결과 불확실, REFUND_PENDING 유지: depositPaymentId={}, impUid={}",
                        target.depositPaymentId(), target.impUid());
                throw e;
            }
            // PG 가 취소를 확실히 거절했으므로 환불 시작 전 상태로 되돌린다
            refundStateService.restore(target);
            throw e;
        }

        try {
            return refundStateService.complete(target, LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            // PG 환불은 끝났는데 DB 반영만 실패한 경우. REFUND_PENDING 으로 남아 보정 대상이 된다
            log.error("포트원 환불 성공 후 DB 반영 실패: depositPaymentId={}, impUid={}, amount={}",
                    target.depositPaymentId(), target.impUid(), target.refundAmount(), e);
            throw new BusinessException(ErrorCode.PAYMENT_REFUND_UNCERTAIN);
        }
    }
}
