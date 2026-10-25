package com.ucamp.app.domain.cancel.policy;

/**
 * 고객 직접 취소의 환불 견적. 화면 조회와 취소 실행 시점에 각각 RefundPolicy 로 계산하고,
 * 두 견적의 refundAmount 가 다르면 고객에게 재확인을 받는다.
 *
 * @param paidAmount          실제 납부 예약금 (deposit_payment.amount)
 * @param refundRate          기본 정책 환불 비율(%). 당일 0, 하루 전 50, 이틀 전까지 100
 * @param policyRefundAmount  기본 정책 환불액 (원 미만 버림). 사장님 대리 취소 환불액의 하한으로도 쓴다
 * @param refundAmount        실제 환불할 금액. 무료 취소 적용 시 납부액 전액, 아니면 기본 정책 환불액
 * @param graceCancelResult   무료 취소 판정 결과
 * @param remainingGraceCount 이번 취소 전 기준 오늘 남은 무료 취소 횟수
 */
public record RefundQuote(
        int paidAmount,
        int refundRate,
        int policyRefundAmount,
        int refundAmount,
        GraceCancelResult graceCancelResult,
        int remainingGraceCount) {

    /**
     * 무료 취소를 적용해 grace_cancel_usage 에 기록해야 하는지 여부.
     */
    public boolean graceApplied() {
        return graceCancelResult == GraceCancelResult.APPLIED;
    }
}
