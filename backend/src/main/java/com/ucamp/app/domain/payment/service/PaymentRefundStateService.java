package com.ucamp.app.domain.payment.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.payment.dto.response.PaymentCancelResponse;
import com.ucamp.app.domain.payment.entity.DepositPayment;
import com.ucamp.app.domain.payment.entity.DepositPaymentStatus;
import com.ucamp.app.domain.payment.repository.DepositPaymentRepository;

/**
 * 환불 상태 변경만 담당하는 짧은 트랜잭션 모음. PG 호출은 {@link PaymentService} 가 트랜잭션 밖에서 한다.
 *
 * <p>같은 클래스 안에서 호출하면 {@code @Transactional} 이 적용되지 않으므로 별도 빈으로 분리했다.
 */
@Service
public class PaymentRefundStateService {

    private final DepositPaymentRepository depositPaymentRepository;

    public PaymentRefundStateService(DepositPaymentRepository depositPaymentRepository) {
        this.depositPaymentRepository = depositPaymentRepository;
    }

    /**
     * 환불을 시작한다. refundAmount 가 null 이면 남은 금액 전액.
     */
    @Transactional
    public RefundTarget start(Long depositPaymentId, Integer refundAmount) {
        DepositPayment payment = findForUpdate(depositPaymentId);
        DepositPaymentStatus previousStatus = payment.getStatus();
        int amount = refundAmount == null ? payment.refundableAmount() : refundAmount;

        payment.startRefund(amount);

        return new RefundTarget(depositPaymentId, payment.getPgTransactionKey(), amount, previousStatus);
    }

    @Transactional
    public PaymentCancelResponse complete(RefundTarget target, LocalDateTime refundedAt) {
        DepositPayment payment = findForUpdate(target.depositPaymentId());
        payment.completeRefund(target.refundAmount(), refundedAt);
        return PaymentCancelResponse.from(payment);
    }

    @Transactional
    public void restore(RefundTarget target) {
        findForUpdate(target.depositPaymentId()).restoreStatus(target.previousStatus());
    }

    private DepositPayment findForUpdate(Long depositPaymentId) {
        return depositPaymentRepository.findByIdForUpdate(depositPaymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
}
