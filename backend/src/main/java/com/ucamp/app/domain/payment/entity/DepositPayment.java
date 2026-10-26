package com.ucamp.app.domain.payment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.entity.BaseTimeEntity;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.reservation.entity.Reservation;

/**
 * 예약금결제. 포트원 V1 의 imp_uid 를 PG_TRANSACTION_KEY 에 저장한다.
 */
@Entity
@Table(name = "DEPOSIT_PAYMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepositPayment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_DEPOSIT_PAYMENT_GENERATOR")
    @SequenceGenerator(name = "SEQ_DEPOSIT_PAYMENT_GENERATOR", sequenceName = "SEQ_DEPOSIT_PAYMENT",
            allocationSize = 1)
    private Long depositPaymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESERVATION_ID", nullable = false)
    private Reservation reservation;

    @Column(nullable = false, length = 20)
    private String pgProvider;

    @Column(length = 200)
    private String pgTransactionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepositPaymentStatus status;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    private Integer refundedAmount;

    private LocalDateTime approvedAt;

    private LocalDateTime refundedAt;

    @Builder
    private DepositPayment(Reservation reservation, String pgProvider, String pgTransactionKey,
                           DepositPaymentStatus status, Integer amount, Integer refundedAmount,
                           LocalDateTime approvedAt) {
        this.reservation = reservation;
        this.pgProvider = pgProvider;
        this.pgTransactionKey = pgTransactionKey;
        this.status = status == null ? DepositPaymentStatus.READY : status;
        this.amount = amount;
        this.refundedAmount = refundedAmount == null ? 0 : refundedAmount;
        this.approvedAt = approvedAt;
    }

    /**
     * 아직 환불하지 않은 금액.
     */
    public int refundableAmount() {
        return amount - refundedAmount;
    }

    /**
     * 환불 요청 전 검증. 포트원 호출 전에 불러 잘못된 요청이 PG 로 나가지 않게 한다.
     */
    public void validateRefund(int refundAmount) {
        if (!status.refundable()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_REFUNDABLE);
        }
        if (refundAmount <= 0 || refundAmount > refundableAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_REFUND_AMOUNT);
        }
    }

    /**
     * 포트원 취소 성공 후 환불 결과를 반영한다. 전액이 환불되면 REFUNDED, 아니면 PART_REFUNDED.
     */
    public void refund(int refundAmount, LocalDateTime refundedAt) {
        validateRefund(refundAmount);
        this.refundedAmount += refundAmount;
        this.status = refundableAmount() == 0
                ? DepositPaymentStatus.REFUNDED
                : DepositPaymentStatus.PART_REFUNDED;
        this.refundedAt = refundedAt;
    }
}
