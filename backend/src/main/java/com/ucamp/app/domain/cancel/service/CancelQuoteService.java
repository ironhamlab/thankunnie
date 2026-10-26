package com.ucamp.app.domain.cancel.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.cancel.dto.response.CancelQuoteResponse;
import com.ucamp.app.domain.cancel.policy.CancelPolicy;
import com.ucamp.app.domain.cancel.policy.RefundPolicy;
import com.ucamp.app.domain.cancel.policy.RefundQuote;
import com.ucamp.app.domain.cancel.repository.CancelTarget;
import com.ucamp.app.domain.cancel.repository.CancelTargetRepository;

/**
 * 취소 환불 견적 조회 (E-02, REQ-047). 취소 실행(E-05)도 같은 계산으로 금액을 다시 확인한다.
 */
@Service
@Transactional(readOnly = true)
public class CancelQuoteService {

    private final CancelTargetRepository cancelTargetRepository;
    private final GraceCancelUsageService graceCancelUsageService;
    private final Clock clock;

    public CancelQuoteService(CancelTargetRepository cancelTargetRepository,
            GraceCancelUsageService graceCancelUsageService, Clock clock) {
        this.cancelTargetRepository = cancelTargetRepository;
        this.graceCancelUsageService = graceCancelUsageService;
        this.clock = clock;
    }

    public CancelQuoteResponse getQuote(AuthUser requester, Long reservationId) {
        LocalDateTime now = LocalDateTime.now(clock);
        CancelTarget target = cancelTargetRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CANCEL_RESERVATION_NOT_FOUND));
        return CancelQuoteResponse.of(reservationId, quote(requester, target, now), now);
    }

    /**
     * 요청자·취소 가능 여부를 검사하고 견적을 계산한다. 무료 취소 사용 여부는 예약한 고객 기준이다.
     */
    RefundQuote quote(AuthUser requester, CancelTarget target, LocalDateTime now) {
        CancelPolicy.validateRequester(requester, target.userId(), target.shopId());
        CancelPolicy.validateCancellable(target.status(), target.startsAt(), now);
        // 확정 예약에 결제 완료 정보가 없으면 데이터 오류다
        if (!target.hasPayment()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        boolean graceUsedToday = graceCancelUsageService.isUsed(target.userId(), RefundPolicy.graceUsageDate(now));
        return RefundPolicy.quote(target.paidAmount(), target.startsAt(), target.approvedAt(), target.heldAt(),
                graceUsedToday, now);
    }
}
