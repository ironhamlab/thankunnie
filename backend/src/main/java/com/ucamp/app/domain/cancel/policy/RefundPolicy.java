package com.ucamp.app.domain.cancel.policy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.ucamp.app.common.policy.BookingPolicy;

/**
 * 고객 취소 환불 계산 (E-02·E-03). DB·시계에 의존하지 않는 순수 함수이며, 현재 시각은 호출하는 쪽이 Clock 으로 구해 넘긴다.
 * 모든 시각은 한국 시간(LocalDateTime, KST) 기준이다.
 * 취소 가능 여부(상태·본인 확인)는 E-04 에서 먼저 검사하고, 시작 시각 이후 호출은 허용하지 않는다.
 */
public final class RefundPolicy {

    /** 무료 취소 가능 시간: 결제 승인 후 */
    public static final Duration GRACE_WINDOW = Duration.ofMinutes(10);

    /** 무료 취소 하루 최대 횟수 (한국 날짜 기준) */
    public static final int GRACE_DAILY_LIMIT = 1;

    private static final int FULL_RATE = 100;
    private static final int DAY_BEFORE_RATE = 50;
    private static final int SAME_DAY_RATE = 0;

    private RefundPolicy() {
    }

    /**
     * 고객 직접 취소 환불 견적을 계산한다.
     *
     * @param paidAmount      실제 납부 예약금
     * @param startsAt        예약 시작 시각
     * @param approvedAt      예약금 결제 승인 시각 (서버 Clock 기준, heldAt 이후·now 이전)
     * @param heldAt          점유(HOLD 생성) 시각
     * @param graceUsedToday  오늘(now 의 날짜) 무료 취소를 이미 사용했는지
     * @param now             판단 시각
     */
    public static RefundQuote quote(int paidAmount, LocalDateTime startsAt, LocalDateTime approvedAt,
            LocalDateTime heldAt, boolean graceUsedToday, LocalDateTime now) {
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(approvedAt, "approvedAt");
        Objects.requireNonNull(heldAt, "heldAt");
        Objects.requireNonNull(now, "now");
        if (paidAmount < 0) {
            throw new IllegalArgumentException("납부액은 0원 이상이어야 합니다: " + paidAmount);
        }
        // 시작 이후 취소는 E-04 에서 막는다. 여기까지 오면 호출 순서 오류다
        if (!now.isBefore(startsAt)) {
            throw new IllegalArgumentException("시술 시작 이후에는 환불 견적을 계산하지 않습니다");
        }
        // 점유 → 결제 승인 → 취소 순서가 뒤집힌 입력은 저장 오류다. 무료 취소(전액 환불)로 판정되지 않도록 막는다.
        // 결제 승인 시각은 서버 Clock 기준으로 저장한다는 전제다 (PG 시각을 쓰면 시계 차이로 정상 요청이 막힐 수 있음)
        if (heldAt.isAfter(approvedAt)) {
            throw new IllegalArgumentException("점유 시각이 결제 승인 시각보다 늦습니다");
        }
        if (approvedAt.isAfter(now)) {
            throw new IllegalArgumentException("결제 승인 시각이 현재 시각보다 늦습니다");
        }

        int refundRate = refundRate(startsAt, now);
        int policyRefundAmount = policyRefundAmount(paidAmount, refundRate);
        GraceCancelResult graceResult = judgeGrace(paidAmount, policyRefundAmount, startsAt, approvedAt,
                heldAt, graceUsedToday, now);
        int refundAmount = graceResult == GraceCancelResult.APPLIED ? paidAmount : policyRefundAmount;
        int remainingGraceCount = graceUsedToday ? 0 : GRACE_DAILY_LIMIT;

        return new RefundQuote(paidAmount, refundRate, policyRefundAmount, refundAmount, graceResult,
                remainingGraceCount);
    }

    /**
     * 시작일과 판단일의 한국 날짜 차이로 환불 비율을 정한다. 시각과 관계없이 자정(00:00)이 경계다.
     * 예: 11/2 10:00 예약을 11/1 23:59 에 취소하면 하루 전(50%), 11/2 00:00 에 취소하면 당일(0%).
     */
    public static int refundRate(LocalDateTime startsAt, LocalDateTime now) {
        long daysBefore = ChronoUnit.DAYS.between(now.toLocalDate(), startsAt.toLocalDate());
        if (daysBefore >= 2) {
            return FULL_RATE;
        }
        if (daysBefore == 1) {
            return DAY_BEFORE_RATE;
        }
        return SAME_DAY_RATE;
    }

    /**
     * 정책 환불액. 원 미만은 버린다 (PRC-12 제안안).
     */
    public static int policyRefundAmount(int paidAmount, int refundRate) {
        return (int) ((long) paidAmount * refundRate / FULL_RATE);
    }

    /**
     * 무료 취소 사용 기록(grace_cancel_usage.used_date)에 넣을 날짜. 서버가 취소 요청을 접수한 한국 날짜다 (CNL-13 제안안).
     */
    public static LocalDate graceUsageDate(LocalDateTime now) {
        return now.toLocalDate();
    }

    /*
     * 판정 순서: 이미 전액 환불이면 다른 조건은 볼 필요가 없고(횟수 미차감),
     * 초임박 점유는 시간이 지나도 바뀌지 않는 조건이라 화면 안내를 위해 10분 경과보다 먼저 본다.
     */
    private static GraceCancelResult judgeGrace(int paidAmount, int policyRefundAmount, LocalDateTime startsAt,
            LocalDateTime approvedAt, LocalDateTime heldAt, boolean graceUsedToday, LocalDateTime now) {
        if (policyRefundAmount >= paidAmount) {
            return GraceCancelResult.NOT_NEEDED;
        }
        if (isLastMinuteHold(startsAt, heldAt)) {
            return GraceCancelResult.LAST_MINUTE_HOLD;
        }
        if (!isWithinGraceWindow(approvedAt, now)) {
            return GraceCancelResult.WINDOW_PASSED;
        }
        if (graceUsedToday) {
            return GraceCancelResult.ALREADY_USED_TODAY;
        }
        return GraceCancelResult.APPLIED;
    }

    // 점유 시점에 시작까지 3시간 이하였으면 초임박. 가격 구간(PriceTier)과 같은 경계를 쓴다
    private static boolean isLastMinuteHold(LocalDateTime startsAt, LocalDateTime heldAt) {
        return Duration.between(heldAt, startsAt).compareTo(BookingPolicy.LAST_MINUTE_FROM) <= 0;
    }

    // 승인 후 정확히 10분이 된 시점은 지난 것으로 본다 (마감 시각과 같은 시점은 마감 — SalePolicy 와 같은 규칙)
    private static boolean isWithinGraceWindow(LocalDateTime approvedAt, LocalDateTime now) {
        return now.isBefore(approvedAt.plus(GRACE_WINDOW));
    }
}
