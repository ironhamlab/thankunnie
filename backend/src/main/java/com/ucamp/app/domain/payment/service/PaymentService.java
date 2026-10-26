package com.ucamp.app.domain.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.payment.client.PortOneClient;
import com.ucamp.app.domain.payment.dto.request.PaymentVerifyRequest;
import com.ucamp.app.domain.payment.dto.response.PaymentCancelResponse;
import com.ucamp.app.domain.payment.dto.response.PaymentVerifyResponse;
import com.ucamp.app.domain.payment.entity.DepositPayment;
import com.ucamp.app.domain.payment.repository.DepositPaymentRepository;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PortOneClient portOneClient;
    private final DepositPaymentRepository depositPaymentRepository;
    private final Clock clock;

    public PaymentService(PortOneClient portOneClient,
                          DepositPaymentRepository depositPaymentRepository,
                          Clock clock) {
        this.portOneClient = portOneClient;
        this.depositPaymentRepository = depositPaymentRepository;
        this.clock = clock;
    }

    // DB 연동 전 단계: 포트원 조회 결과만 반환한다. 이후 주문 금액 비교·저장을 여기에 추가
    public PaymentVerifyResponse verify(PaymentVerifyRequest request) {
        return PaymentVerifyResponse.from(portOneClient.getPayment(request.impUid()));
    }

    /**
     * 예약금결제의 남은 금액을 전액 환불한다.
     */
    @Transactional
    public PaymentCancelResponse cancel(Long depositPaymentId, String reason) {
        DepositPayment payment = findForUpdate(depositPaymentId);
        return refund(payment, payment.refundableAmount(), reason);
    }

    /**
     * 예약금결제를 지정 금액만큼 환불한다. 예약 취소처럼 환불액을 정책으로 계산하는 다른 도메인에서 호출한다.
     *
     * @param refundAmount 환불 금액 (남은 금액 이하)
     */
    @Transactional
    public PaymentCancelResponse refund(Long depositPaymentId, int refundAmount, String reason) {
        return refund(findForUpdate(depositPaymentId), refundAmount, reason);
    }

    private PaymentCancelResponse refund(DepositPayment payment, int refundAmount, String reason) {
        // 포트원에 잘못된 취소 요청이 나가지 않도록 먼저 검증한다
        payment.validateRefund(refundAmount);

        portOneClient.cancelPayment(payment.getPgTransactionKey(), refundAmount, reason);
        payment.refund(refundAmount, LocalDateTime.now(clock));

        return PaymentCancelResponse.from(payment);
    }

    private DepositPayment findForUpdate(Long depositPaymentId) {
        return depositPaymentRepository.findByIdForUpdate(depositPaymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
}
