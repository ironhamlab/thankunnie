package com.ucamp.app.domain.payment.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.payment.client.PortOneClient;
import com.ucamp.app.domain.payment.client.PortOnePayment;
import com.ucamp.app.domain.payment.dto.request.PaymentVerifyRequest;
import com.ucamp.app.domain.payment.dto.response.PaymentCancelResponse;
import com.ucamp.app.domain.payment.dto.response.PaymentVerifyResponse;
import com.ucamp.app.common.auth.UserRole;

@Slf4j
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final String PORTONE_PAID = "paid";
    private static final String AUTO_REFUND_REASON = "예약을 확정할 수 없어 결제를 자동 취소합니다";

    // 결제는 됐지만 예약을 확정할 수 없는 경우. 돈만 빠져나가지 않도록 결제를 바로 취소한다.
    // 요청사항이 너무 긴 경우(INVALID_INPUT)는 고쳐서 같은 결제로 다시 확인할 수 있으므로 취소하지 않는다
    private static final Set<ErrorCode> AUTO_REFUND_ERRORS = Set.of(
            ErrorCode.RESERVATION_NOT_FOUND,
            ErrorCode.RESERVATION_ALREADY_CONFIRMED,
            ErrorCode.RESERVATION_HOLD_EXPIRED,
            ErrorCode.RESERVATION_NOT_HOLD,
            ErrorCode.RESERVATION_DEPOSIT_MISMATCH,
            ErrorCode.VACANCY_PAYMENT_CLOSED);

    private final PortOneClient portOneClient;
    private final PaymentRefundStateService refundStateService;
    private final PaymentConfirmService confirmService;
    private final Clock clock;

    public PaymentService(PortOneClient portOneClient,
                          PaymentRefundStateService refundStateService,
                          PaymentConfirmService confirmService,
                          Clock clock) {
        this.portOneClient = portOneClient;
        this.refundStateService = refundStateService;
        this.confirmService = confirmService;
        this.clock = clock;
    }

    /**
     * 결제창에서 끝난 예약금 결제를 확인하고 예약을 확정한다.
     * 포트원에서 결제 완료(paid)와 주문번호를 확인한 뒤, 예약 확정과 예약금결제 저장을 한 트랜잭션으로 한다.
     * 확정할 수 없으면(점유 만료·금액 불일치·이미 확정 등) 결제를 바로 취소하고 그 오류를 그대로 던진다.
     *
     * <p>PG 호출을 DB 트랜잭션 밖에서 하므로 트랜잭션은 확정·저장 구간에만 둔다.
     *
     * @throws BusinessException 고객이 아니면 FORBIDDEN, 결제가 완료되지 않았으면 PAYMENT_NOT_PAID,
     *                           주문번호가 이 예약의 것이 아니면 PAYMENT_RESERVATION_MISMATCH, 그 밖에는 예약 확정 오류
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentVerifyResponse verify(AuthUser loginUser, PaymentVerifyRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        Long reservationId = request.reservationId();
        PortOnePayment payment = portOneClient.getPayment(request.impUid());
        if (!PORTONE_PAID.equals(payment.status())) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_PAID);
        }
        // 다른 예약에서 결제한 imp_uid 를 이 예약에 가져다 쓰지 못하게 한다
        if (payment.merchantUid() == null || !payment.merchantUid().startsWith(merchantUidPrefix(reservationId))) {
            throw new BusinessException(ErrorCode.PAYMENT_RESERVATION_MISMATCH);
        }
        int paidAmount = Math.toIntExact(payment.amount());
        try {
            Long depositPaymentId = confirmService.confirm(loginUser.userId(), reservationId, payment.impUid(),
                    paidAmount, request.customerRequest(), approvedAt(payment));
            return PaymentVerifyResponse.of(payment, reservationId, depositPaymentId);
        } catch (BusinessException e) {
            // 같은 결제가 동시에 두 번 들어와 다른 요청이 먼저 확정했으면 실패가 아니다. 이때 취소하면 정상 결제가 환불된다
            Optional<Long> saved = confirmService.findSaved(payment.impUid(), reservationId);
            if (saved.isPresent()) {
                return PaymentVerifyResponse.of(payment, reservationId, saved.get());
            }
            if (AUTO_REFUND_ERRORS.contains(e.getErrorCode())) {
                cancelUnconfirmedPayment(payment, paidAmount, e.getErrorCode());
            }
            throw e;
        }
    }

    /**
     * 결제창 주문번호(merchant_uid) 앞부분. 프론트는 "reservation-{예약 ID}-{고유값}" 으로 만든다.
     */
    public static String merchantUidPrefix(Long reservationId) {
        return "reservation-" + reservationId + "-";
    }

    // 취소에 실패하면 결제만 남으므로 운영자가 찾을 수 있게 남기고, 고객에게는 확정 실패 사유를 그대로 알린다
    private void cancelUnconfirmedPayment(PortOnePayment payment, int amount, ErrorCode reason) {
        try {
            portOneClient.cancelPayment(payment.impUid(), amount, AUTO_REFUND_REASON);
            log.info("예약 확정 실패로 결제 자동 취소: impUid={}, reason={}", payment.impUid(), reason);
        } catch (BusinessException cancelError) {
            log.error("예약 확정 실패 후 결제 자동 취소 실패, 수동 확인 필요: impUid={}, amount={}, reason={}, cancelError={}",
                    payment.impUid(), amount, reason, cancelError.getErrorCode());
        }
    }

    // 포트원은 결제 시각을 Unix 초로 준다. 없으면 확인한 시각을 쓴다
    private LocalDateTime approvedAt(PortOnePayment payment) {
        if (payment.paidAt() == null || payment.paidAt() == 0) {
            return LocalDateTime.now(clock);
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(payment.paidAt()), TimeUtils.KST);
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
