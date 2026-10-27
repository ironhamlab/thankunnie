package com.ucamp.app.domain.cancel.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.cancel.policy.GraceCancelResult;
import com.ucamp.app.domain.cancel.policy.RefundQuote;

/**
 * 취소 환불 견적. 고객 취소 확인 화면(E-F1)과 사장님 대리 취소 환불액 입력 범위(E-F2)에 쓴다.
 * 사장님 대리 취소 환불액은 policyRefundAmount 이상, paidAmount 이하로 입력한다.
 *
 * @param refundAmount        고객 직접 취소 시 실제 환불액 (무료 취소 반영)
 * @param graceApplied        무료 취소 적용 여부. 적용되면 오늘 무료 취소 횟수가 차감된다
 * @param remainingGraceCount 이번 취소 전 기준 오늘 남은 무료 취소 횟수
 * @param quotedAt            견적 계산 시각. 실행 시점에 다시 계산해 금액이 다르면 재확인을 받는다
 */
public record CancelQuoteResponse(
        Long reservationId,
        int paidAmount,
        int refundRate,
        int policyRefundAmount,
        int refundAmount,
        GraceCancelResult graceCancelResult,
        boolean graceApplied,
        int remainingGraceCount,
        OffsetDateTime quotedAt) {

    public static CancelQuoteResponse of(Long reservationId, RefundQuote quote, LocalDateTime quotedAt) {
        return new CancelQuoteResponse(
                reservationId,
                quote.paidAmount(),
                quote.refundRate(),
                quote.policyRefundAmount(),
                quote.refundAmount(),
                quote.graceCancelResult(),
                quote.graceApplied(),
                quote.remainingGraceCount(),
                TimeUtils.toOffset(quotedAt));
    }
}
