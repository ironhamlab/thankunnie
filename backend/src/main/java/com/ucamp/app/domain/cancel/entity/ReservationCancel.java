package com.ucamp.app.domain.cancel.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.entity.BaseTimeEntity;

/**
 * 예약 취소. 예약당 1건만 저장되며(UNIQUE), 결제 취소 실패 후 재시도는 같은 행을 갱신한다.
 * 예약·회원은 다른 도메인 엔티티를 직접 참조하지 않고 ID 로만 참조한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "RESERVATION_CANCEL")
public class ReservationCancel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_reservation_cancel")
    @SequenceGenerator(name = "seq_reservation_cancel", sequenceName = "SEQ_RESERVATION_CANCEL", allocationSize = 1)
    @Column(name = "CANCEL_ID")
    private Long id;

    @Column(name = "RESERVATION_ID", nullable = false)
    private Long reservationId;

    /**
     * 예약한 고객의 회원 ID. 매장 취소도 고객 ID 를 넣는다 (매장은 APP_USER 가 아니라 FK 를 만족할 수 없음).
     * 누가 취소했는지는 cancelType 으로 구분한다.
     */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "CANCEL_TYPE", nullable = false, length = 20)
    private CancelType cancelType;

    /** 고객 정책 환불액 (기본 정책 기준, 무료 취소 미반영) */
    @Column(name = "POLICY_REFUND_AMOUNT", nullable = false)
    private Integer policyRefundAmount;

    /** 실제 환불할 금액 */
    @Column(name = "REFUND_AMOUNT", nullable = false)
    private Integer refundAmount;

    @Column(name = "PG_CANCEL_KEY", length = 200)
    private String pgCancelKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private CancelStatus status;

    @Column(name = "REQUESTED_AT", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    @Builder
    private ReservationCancel(Long reservationId, Long userId, CancelType cancelType,
            Integer policyRefundAmount, Integer refundAmount, LocalDateTime requestedAt) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.cancelType = cancelType;
        this.policyRefundAmount = policyRefundAmount;
        this.refundAmount = refundAmount;
        this.requestedAt = requestedAt;
        this.status = CancelStatus.REQUESTED;
    }

    /**
     * 결제 취소 성공.
     */
    public void complete(String pgCancelKey, LocalDateTime completedAt) {
        requireStatus(CancelStatus.REQUESTED, CancelStatus.UNKNOWN);
        this.status = CancelStatus.COMPLETED;
        this.pgCancelKey = pgCancelKey;
        this.completedAt = completedAt;
    }

    /**
     * 결제 취소 실패. 예약을 CONFIRMED 로 되돌리고 무료 취소 기록을 지우는 것은 서비스에서 함께 처리한다.
     */
    public void fail() {
        requireStatus(CancelStatus.REQUESTED, CancelStatus.UNKNOWN);
        this.status = CancelStatus.FAILED;
    }

    /**
     * 결제 취소 결과 불명 (통신 오류·타임아웃).
     */
    public void markUnknown() {
        requireStatus(CancelStatus.REQUESTED);
        this.status = CancelStatus.UNKNOWN;
    }

    /**
     * 실패한 취소를 같은 행으로 다시 접수한다. 재시도 시점의 견적으로 금액을 갱신한다.
     */
    public void retry(CancelType cancelType, Integer policyRefundAmount, Integer refundAmount,
            LocalDateTime requestedAt) {
        requireStatus(CancelStatus.FAILED);
        this.cancelType = cancelType;
        this.policyRefundAmount = policyRefundAmount;
        this.refundAmount = refundAmount;
        this.requestedAt = requestedAt;
        this.pgCancelKey = null;
        this.completedAt = null;
        this.status = CancelStatus.REQUESTED;
    }

    private void requireStatus(CancelStatus... allowed) {
        for (CancelStatus candidate : allowed) {
            if (this.status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("취소 상태 " + status + " 에서는 처리할 수 없습니다");
    }
}
