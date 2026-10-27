package com.ucamp.app.domain.cancel.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.cancel.dto.request.OwnerCancelRequest;
import com.ucamp.app.domain.cancel.entity.CancelStatus;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.cancel.entity.ReservationCancel;
import com.ucamp.app.domain.cancel.policy.CancelPolicy;
import com.ucamp.app.domain.cancel.policy.RefundPolicy;
import com.ucamp.app.domain.cancel.policy.RefundQuote;
import com.ucamp.app.domain.cancel.repository.CancelTarget;
import com.ucamp.app.domain.cancel.repository.ReservationCancelRepository;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;
import com.ucamp.app.domain.schedule.entity.ShopScheduleException;
import com.ucamp.app.domain.schedule.repository.ShopScheduleExceptionRepository;

/**
 * 취소 접수 (E-05). 결제 취소(PG 호출) 전에 이 트랜잭션을 먼저 커밋해, 결제 취소 중에도 시간이 막혀 있게 한다 (CNL-21·22).
 */
@Service
@Transactional(readOnly = true)
public class CancelRequestService {

    private final CancelQuoteService cancelQuoteService;
    private final ReservationRepository reservationRepository;
    private final ReservationCancelRepository reservationCancelRepository;
    private final ShopScheduleExceptionRepository shopScheduleExceptionRepository;
    private final GraceCancelUsageService graceCancelUsageService;

    public CancelRequestService(CancelQuoteService cancelQuoteService,
            ReservationRepository reservationRepository,
            ReservationCancelRepository reservationCancelRepository,
            ShopScheduleExceptionRepository shopScheduleExceptionRepository,
            GraceCancelUsageService graceCancelUsageService) {
        this.cancelQuoteService = cancelQuoteService;
        this.reservationRepository = reservationRepository;
        this.reservationCancelRepository = reservationCancelRepository;
        this.shopScheduleExceptionRepository = shopScheduleExceptionRepository;
        this.graceCancelUsageService = graceCancelUsageService;
    }

    /**
     * 고객 직접 취소를 접수한다. 실행 시점에 견적을 다시 계산해 화면 견적과 다르면 재확인을 받는다 (CNL-14).
     * 무료 취소를 쓰면 이 트랜잭션에서 기록하고, 다른 요청이 먼저 썼으면 접수 전체가 롤백된다.
     *
     * @param expectedRefundAmount 고객이 화면에서 확인한 환불액
     */
    @Transactional
    public AcceptedCancel acceptCustomerCancel(AuthUser requester, Long reservationId, int expectedRefundAmount,
            LocalDateTime now) {
        CancelPolicy.validateCancelType(requester, CancelType.CUSTOMER);
        CancelTarget target = cancelQuoteService.load(reservationId);
        RefundQuote quote = cancelQuoteService.quote(requester, target, now);
        if (quote.refundAmount() != expectedRefundAmount) {
            throw new BusinessException(ErrorCode.CANCEL_QUOTE_CHANGED);
        }

        changeToCancelPending(reservationId, now);
        ReservationCancel cancel = saveCancel(target, CancelType.CUSTOMER, quote.policyRefundAmount(),
                quote.refundAmount(), now);
        if (quote.graceApplied()) {
            graceCancelUsageService.record(target.userId(), RefundPolicy.graceUsageDate(now), cancel.getId());
        }
        return accepted(cancel, target, quote.refundAmount(), false);
    }

    /**
     * 사장님 취소를 접수한다. 매장 사유는 납부액 전액, 고객 요청 대리 취소는 사장님이 입력한 금액을 환불한다 (CNL-17·18).
     * 고객 무료 취소 횟수는 차감하지 않는다 (CNL-17·19). 닫기를 고르면 취소한 시간을 바로 닫아 둔다 (REO-02).
     * 결제 취소 중에도 예약이 시간을 막고 있으므로 먼저 닫아도 겹치지 않고, 결과가 불명이어도 선택이 남는다.
     */
    @Transactional
    public AcceptedCancel acceptOwnerCancel(AuthUser requester, Long reservationId, OwnerCancelRequest request,
            LocalDateTime now) {
        CancelPolicy.validateCancelType(requester, request.cancelType());
        CancelTarget target = cancelQuoteService.load(reservationId);
        RefundQuote quote = cancelQuoteService.quote(requester, target, now);
        int refundAmount = ownerRefundAmount(request, quote);

        changeToCancelPending(reservationId, now);
        ReservationCancel cancel = saveCancel(target, request.cancelType(), quote.policyRefundAmount(), refundAmount,
                now);
        boolean close = Boolean.TRUE.equals(request.closeAfterCancel());
        if (close) {
            shopScheduleExceptionRepository.save(ShopScheduleException.builder()
                    .shopId(target.shopId())
                    .reservationId(reservationId)
                    .exceptionType(ScheduleExceptionType.CLOSE)
                    .startsAt(target.startsAt())
                    .endsAt(target.endsAt())
                    .reason(request.reason())
                    .build());
        }
        return accepted(cancel, target, refundAmount, close);
    }

    private int ownerRefundAmount(OwnerCancelRequest request, RefundQuote quote) {
        if (request.cancelType() == CancelType.SHOP_FAULT) {
            return CancelPolicy.shopFaultRefundAmount(quote.paidAmount());
        }
        if (request.refundAmount() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "고객 요청 대리 취소는 환불액을 입력해야 합니다.");
        }
        CancelPolicy.validateProxyRefundAmount(request.refundAmount(), quote.policyRefundAmount(), quote.paidAmount());
        return request.refundAmount();
    }

    private AcceptedCancel accepted(ReservationCancel cancel, CancelTarget target, int refundAmount, boolean closed) {
        return new AcceptedCancel(cancel.getId(), target.reservationId(), target.depositPaymentId(), refundAmount,
                closed, target.customerName(), target.customerPhone());
    }

    // 확정 상태일 때만 바뀌므로 동시에 들어온 취소는 한 건만 통과한다
    private void changeToCancelPending(Long reservationId, LocalDateTime now) {
        int changed = reservationRepository.changeStatus(reservationId, ReservationStatus.CONFIRMED,
                ReservationStatus.CANCEL_PENDING, now);
        if (changed == 0) {
            throw new BusinessException(ErrorCode.CANCEL_ALREADY_REQUESTED);
        }
    }

    // 결제 취소에 실패했던 예약은 같은 행으로 다시 접수한다 (UNIQUE: 예약당 취소 1건)
    private ReservationCancel saveCancel(CancelTarget target, CancelType cancelType, int policyRefundAmount,
            int refundAmount, LocalDateTime now) {
        Optional<ReservationCancel> existing = reservationCancelRepository.findByReservationId(target.reservationId());
        if (existing.isPresent()) {
            ReservationCancel cancel = existing.get();
            if (cancel.getStatus() != CancelStatus.FAILED) {
                throw new BusinessException(ErrorCode.CANCEL_ALREADY_REQUESTED);
            }
            cancel.retry(cancelType, policyRefundAmount, refundAmount, now);
            return cancel;
        }
        try {
            return reservationCancelRepository.saveAndFlush(ReservationCancel.builder()
                    .reservationId(target.reservationId())
                    .userId(target.userId())
                    .cancelType(cancelType)
                    .policyRefundAmount(policyRefundAmount)
                    .refundAmount(refundAmount)
                    .requestedAt(now)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CANCEL_ALREADY_REQUESTED);
        }
    }
}
