package com.ucamp.app.domain.cancel.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RefundPolicyTest {

    /** 시작 2026-11-02 10:00. 점유·결제는 기본적으로 사흘 전 (초임박 아님, 10분 경과) */
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final LocalDateTime HELD_AT = LocalDateTime.of(2026, 10, 30, 12, 0);
    private static final LocalDateTime APPROVED_AT = HELD_AT.plusMinutes(2);

    @ParameterizedTest(name = "{0} 취소 → {1}%")
    @CsvSource({
            "2026-10-30T12:30, 100",   // 사흘 전
            "2026-10-31T23:59, 100",   // 이틀 전 마지막
            "2026-11-01T00:00, 50",    // 하루 전 시작 (자정 경계)
            "2026-11-01T23:59:59, 50", // 하루 전 마지막
            "2026-11-02T00:00, 0",     // 당일 시작
            "2026-11-02T09:59, 0"      // 시작 1분 전
    })
    @DisplayName("환불 비율은 한국 날짜 차이로 정하고 자정이 경계다")
    void refundRate_byKoreanDate(LocalDateTime now, int expectedRate) {
        assertThat(RefundPolicy.refundRate(STARTS_AT, now)).isEqualTo(expectedRate);
    }

    @ParameterizedTest(name = "납부 {0}원, {1} 취소 → {2}원")
    @CsvSource({
            "20000, 2026-11-02T08:00, 0",
            "20000, 2026-11-01T12:00, 10000",
            "20000, 2026-10-31T12:00, 20000",
            "15000, 2026-11-02T08:00, 0",
            "15000, 2026-11-01T12:00, 7500",
            "15000, 2026-10-31T12:00, 15000"
    })
    @DisplayName("정책서 환불 계산 예시와 같은 금액을 계산한다")
    void quote_matchesPolicyExamples(int paidAmount, LocalDateTime now, int expectedRefund) {
        RefundQuote quote = RefundPolicy.quote(paidAmount, STARTS_AT, APPROVED_AT, HELD_AT, false, now);

        assertThat(quote.paidAmount()).isEqualTo(paidAmount);
        assertThat(quote.policyRefundAmount()).isEqualTo(expectedRefund);
        assertThat(quote.refundAmount()).isEqualTo(expectedRefund);
        assertThat(quote.graceApplied()).isFalse();
    }

    @Test
    @DisplayName("50% 환불에서 원 미만은 버린다")
    void policyRefundAmount_floorsBelowOneWon() {
        assertThat(RefundPolicy.policyRefundAmount(15_001, 50)).isEqualTo(7_500);
    }

    @Test
    @DisplayName("결제 후 10분 이내이고 초임박 점유가 아니며 오늘 미사용이면 무료 취소로 전액 환불한다")
    void quote_graceApplied_fullRefund() {
        LocalDateTime heldAt = LocalDateTime.of(2026, 11, 2, 6, 0);   // 시작 4시간 전 점유 → 당일 0%
        LocalDateTime approvedAt = heldAt.plusMinutes(3);
        LocalDateTime now = approvedAt.plusMinutes(9).plusSeconds(59);

        RefundQuote quote = RefundPolicy.quote(20_000, STARTS_AT, approvedAt, heldAt, false, now);

        assertThat(quote.refundRate()).isZero();
        assertThat(quote.policyRefundAmount()).isZero();
        assertThat(quote.refundAmount()).isEqualTo(20_000);
        assertThat(quote.graceCancelResult()).isEqualTo(GraceCancelResult.APPLIED);
        assertThat(quote.graceApplied()).isTrue();
        assertThat(quote.remainingGraceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("결제 후 정확히 10분이 지나면 무료 취소를 적용하지 않는다")
    void quote_exactlyTenMinutesAfterApproval_windowPassed() {
        LocalDateTime heldAt = LocalDateTime.of(2026, 11, 1, 9, 0);   // 하루 전 → 50%
        LocalDateTime approvedAt = heldAt.plusMinutes(1);

        RefundQuote quote = RefundPolicy.quote(20_000, STARTS_AT, approvedAt, heldAt, false,
                approvedAt.plusMinutes(10));

        assertThat(quote.graceCancelResult()).isEqualTo(GraceCancelResult.WINDOW_PASSED);
        assertThat(quote.refundAmount()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("점유 시점에 시작까지 정확히 3시간이면 초임박이라 무료 취소 대상이 아니다")
    void quote_heldExactlyThreeHoursBefore_lastMinuteHold() {
        LocalDateTime heldAt = STARTS_AT.minusHours(3);
        LocalDateTime approvedAt = heldAt.plusMinutes(2);

        RefundQuote quote = RefundPolicy.quote(20_000, STARTS_AT, approvedAt, heldAt, false,
                approvedAt.plusMinutes(1));

        assertThat(quote.graceCancelResult()).isEqualTo(GraceCancelResult.LAST_MINUTE_HOLD);
        assertThat(quote.refundAmount()).isZero();
    }

    @Test
    @DisplayName("점유 시점에 시작까지 3시간 1분이면 무료 취소 대상이다")
    void quote_heldJustOverThreeHoursBefore_graceApplied() {
        LocalDateTime heldAt = STARTS_AT.minusHours(3).minusMinutes(1);
        LocalDateTime approvedAt = heldAt.plusMinutes(2);

        RefundQuote quote = RefundPolicy.quote(20_000, STARTS_AT, approvedAt, heldAt, false,
                approvedAt.plusMinutes(1));

        assertThat(quote.graceCancelResult()).isEqualTo(GraceCancelResult.APPLIED);
    }

    @Test
    @DisplayName("오늘 무료 취소를 이미 썼으면 기본 정책 환불액만 돌려주고 남은 횟수는 0이다")
    void quote_alreadyUsedToday_policyRefundOnly() {
        LocalDateTime heldAt = LocalDateTime.of(2026, 11, 1, 9, 0);
        LocalDateTime approvedAt = heldAt.plusMinutes(1);

        RefundQuote quote = RefundPolicy.quote(20_000, STARTS_AT, approvedAt, heldAt, true,
                approvedAt.plusMinutes(5));

        assertThat(quote.graceCancelResult()).isEqualTo(GraceCancelResult.ALREADY_USED_TODAY);
        assertThat(quote.refundAmount()).isEqualTo(10_000);
        assertThat(quote.remainingGraceCount()).isZero();
    }

    @Test
    @DisplayName("기본 정책으로 전액 환불이면 10분 이내라도 무료 취소를 쓰지 않는다 (횟수 미차감)")
    void quote_alreadyFullRefund_graceNotNeeded() {
        RefundQuote quote = RefundPolicy.quote(20_000, STARTS_AT, APPROVED_AT, HELD_AT, false,
                APPROVED_AT.plusMinutes(5));

        assertThat(quote.refundRate()).isEqualTo(100);
        assertThat(quote.graceCancelResult()).isEqualTo(GraceCancelResult.NOT_NEEDED);
        assertThat(quote.graceApplied()).isFalse();
        assertThat(quote.refundAmount()).isEqualTo(20_000);
        assertThat(quote.remainingGraceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("초임박 점유와 10분 경과가 겹치면 초임박 사유를 우선 안내한다")
    void quote_lastMinuteAndWindowPassed_lastMinuteFirst() {
        LocalDateTime heldAt = STARTS_AT.minusHours(2);
        LocalDateTime approvedAt = heldAt.plusMinutes(2);

        RefundQuote quote = RefundPolicy.quote(20_000, STARTS_AT, approvedAt, heldAt, false,
                approvedAt.plusMinutes(30));

        assertThat(quote.graceCancelResult()).isEqualTo(GraceCancelResult.LAST_MINUTE_HOLD);
    }

    @Test
    @DisplayName("시작 시각 이후에는 견적을 계산하지 않는다")
    void quote_atOrAfterStart_throws() {
        assertThatThrownBy(() -> RefundPolicy.quote(20_000, STARTS_AT, APPROVED_AT, HELD_AT, false, STARTS_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("무료 취소 사용일은 취소 요청을 접수한 한국 날짜다")
    void graceUsageDate_isRequestDate() {
        assertThat(RefundPolicy.graceUsageDate(LocalDateTime.of(2026, 11, 1, 23, 59, 59)))
                .isEqualTo(LocalDate.of(2026, 11, 1));
    }
}
