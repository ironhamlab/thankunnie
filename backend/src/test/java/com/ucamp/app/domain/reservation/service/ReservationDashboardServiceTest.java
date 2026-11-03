package com.ucamp.app.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.reservation.dto.response.ReservationDashboardResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationPeriodSummary;
import com.ucamp.app.domain.reservation.dto.response.ReservationStatsResponse;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationDashboardRepository;
import com.ucamp.app.domain.reservation.repository.ReservationDashboardRow;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

class ReservationDashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 2, 10);
    private static final LocalDateTime NOW = TODAY.atTime(12, 0);
    private static final LocalDate FEB_1 = LocalDate.of(2027, 2, 1);
    private static final LocalDate FEB_28 = LocalDate.of(2027, 2, 28);
    private static final AuthUser OWNER = new AuthUser(11L, UserRole.OWNER, 1L);

    private ReservationDashboardRepository reservationDashboardRepository;
    private AvailabilityService availabilityService;
    private ReservationDashboardService reservationDashboardService;

    @BeforeEach
    void setUp() {
        reservationDashboardRepository = mock(ReservationDashboardRepository.class);
        availabilityService = mock(AvailabilityService.class);
        Clock clock = Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);
        reservationDashboardService =
                new ReservationDashboardService(reservationDashboardRepository, availabilityService, clock);
    }

    @Test
    @DisplayName("대시보드는 오늘 확정·남은 예약, 남은 빈 시간대, 이번 달 요약을 내려준다")
    void getOwnerDashboard_owner_buildsTodayAndMonth() {
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                confirmed(1L, LocalDateTime.of(2027, 1, 31, 14, 0), 60, 10000, LocalDateTime.of(2027, 1, 20, 9, 0)),
                confirmed(2L, TODAY.atTime(10, 0), 60, 30000, LocalDateTime.of(2027, 2, 1, 9, 0)),
                confirmed(3L, TODAY.atTime(15, 0), 60, 20000, LocalDateTime.of(2027, 2, 1, 9, 0)),
                confirmed(4L, LocalDateTime.of(2027, 2, 11, 14, 0), 60, 25000, LocalDateTime.of(2027, 2, 1, 9, 0))));
        // 13:00·13:30 은 이어지고 16:00 은 떨어져 있다
        given(availabilityService.getAvailableStartTimes(1L, TODAY, 30)).willReturn(List.of(
                TODAY.atTime(13, 0), TODAY.atTime(13, 30), TODAY.atTime(16, 0)));
        given(availabilityService.hasOpenTime(1L, TODAY)).willReturn(true);

        ReservationDashboardResponse response = reservationDashboardService.getOwnerDashboard(OWNER);

        // 이번 달 경계의 취소 시간과 겹치는 예약을 놓치지 않도록 앞뒤 하루씩 넓혀 읽는다
        verify(reservationDashboardRepository).findRows(1L, LocalDateTime.of(2027, 1, 31, 0, 0),
                LocalDateTime.of(2027, 3, 2, 0, 0));
        assertThat(response.date()).isEqualTo(TODAY);
        ReservationDashboardResponse.Today today = response.today();
        assertThat(today.confirmedCount()).isEqualTo(2);
        assertThat(today.upcomingCount()).isEqualTo(1);
        assertThat(today.closed()).isFalse();
        assertThat(today.availableMinutes()).isEqualTo(90);
        assertThat(today.availableRanges()).containsExactly(
                new ReservationDashboardResponse.AvailableRange(kst(13, 0), kst(14, 0)),
                new ReservationDashboardResponse.AvailableRange(kst(16, 0), kst(16, 30)));
        // 1월 31일 예약은 이번 달 요약에서 빠진다
        ReservationPeriodSummary month = response.month();
        assertThat(month.from()).isEqualTo(FEB_1);
        assertThat(month.to()).isEqualTo(FEB_28);
        assertThat(month.confirmedCount()).isEqualTo(3);
        assertThat(month.bookedAmount()).isEqualTo(75000);
    }

    @Test
    @DisplayName("열린 시간이 없는 날은 휴무로 표시하고 빈 시간은 0 이다")
    void getOwnerDashboard_closedDay_marksClosed() {
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of());
        given(availabilityService.getAvailableStartTimes(any(), any(), eq(30))).willReturn(List.of());
        given(availabilityService.hasOpenTime(1L, TODAY)).willReturn(false);

        ReservationDashboardResponse.Today today = reservationDashboardService.getOwnerDashboard(OWNER).today();

        assertThat(today.closed()).isTrue();
        assertThat(today.availableMinutes()).isZero();
        assertThat(today.availableRanges()).isEmpty();
    }

    @Test
    @DisplayName("요약은 확정 금액, 고객·매장 사유 취소, 고객 취소율, 남은 예약금, 초임박 판매를 센다")
    void summarize_countsAmountsCancelsAndLastMinute() {
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                confirmed(1L, LocalDateTime.of(2027, 2, 3, 10, 0), 60, 30000, LocalDateTime.of(2027, 1, 20, 9, 0)),
                // 점유 시각이 정확히 시작 3시간 전 → 초임박 (경계는 더 임박한 구간)
                confirmed(2L, LocalDateTime.of(2027, 2, 4, 10, 0), 60, 20000, LocalDateTime.of(2027, 2, 4, 7, 0)),
                cancelled(3L, LocalDateTime.of(2027, 2, 5, 10, 0), 60, CancelType.CUSTOMER, 20000, false),
                cancelled(4L, LocalDateTime.of(2027, 2, 6, 10, 0), 60, CancelType.CUSTOMER_REQUEST, 15000, false),
                cancelled(5L, LocalDateTime.of(2027, 2, 7, 10, 0), 60, CancelType.SHOP_FAULT, 0, true),
                // 기간 밖
                confirmed(6L, LocalDateTime.of(2027, 3, 1, 10, 0), 60, 99000, LocalDateTime.of(2027, 2, 1, 9, 0))));

        ReservationPeriodSummary summary = reservationDashboardService.getOwnerStats(OWNER, FEB_1, FEB_28).summary();

        assertThat(summary.confirmedCount()).isEqualTo(2);
        assertThat(summary.bookedAmount()).isEqualTo(50000);
        assertThat(summary.customerCancelCount()).isEqualTo(2);
        assertThat(summary.shopFaultCancelCount()).isEqualTo(1);
        // 고객 취소 2 ÷ (확정 2 + 고객 취소 2 + 매장 사유 1)
        assertThat(summary.customerCancelRate()).isEqualTo(40.0);
        assertThat(summary.retainedDepositAmount()).isEqualTo(35000);
        assertThat(summary.lastMinuteCount()).isEqualTo(1);
        assertThat(summary.lastMinuteAmount()).isEqualTo(20000);
        // 닫은 매장 사유 취소는 비었던 시간에서 뺀다
        assertThat(summary.cancelledMinutes()).isEqualTo(120);
        assertThat(summary.rebookedMinutes()).isZero();
        assertThat(summary.rebookRate()).isEqualTo(0.0);
        assertThat(summary.rebookedAmount()).isZero();
    }

    @Test
    @DisplayName("90분 취소 2건 자리에 60분 예약 3건이 들어오면 비었던 3시간을 모두 다시 채운 것이다")
    void summarize_rebookByTime_fullyRefilled() {
        LocalDateTime completedAt = LocalDateTime.of(2027, 2, 8, 9, 0);
        LocalDateTime createdAt = LocalDateTime.of(2027, 2, 9, 9, 0);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                cancelled(1L, at(12, 13, 0), 90, CancelType.CUSTOMER, 0, false, completedAt),
                cancelled(2L, at(12, 14, 30), 90, CancelType.CUSTOMER, 0, false, completedAt),
                confirmed(3L, at(12, 13, 0), 60, 20000, createdAt),
                confirmed(4L, at(12, 14, 0), 60, 20000, createdAt),
                confirmed(5L, at(12, 15, 0), 60, 20000, createdAt)));

        ReservationPeriodSummary summary = reservationDashboardService.getOwnerStats(OWNER, FEB_1, FEB_28).summary();

        assertThat(summary.cancelledMinutes()).isEqualTo(180);
        assertThat(summary.rebookedMinutes()).isEqualTo(180);
        assertThat(summary.rebookRate()).isEqualTo(100.0);
        assertThat(summary.rebookedAmount()).isEqualTo(60000);
    }

    @Test
    @DisplayName("겹친 부분만 채운 시간으로 세고, 취소 완료 전 생성 예약과 닫은 시간은 빼며, 예약 금액은 한 번만 센다")
    void summarize_rebookByTime_partialAndExclusions() {
        LocalDateTime completedAt = LocalDateTime.of(2027, 2, 8, 9, 0);
        LocalDateTime createdAt = LocalDateTime.of(2027, 2, 9, 9, 0);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                // 13:00~14:00 취소 → 13:30~14:30 예약이 30분만 채움
                cancelled(1L, at(12, 13, 0), 60, CancelType.CUSTOMER, 0, false, completedAt),
                confirmed(2L, at(12, 13, 30), 60, 30000, createdAt),
                // 매장이 취소하며 닫은 시간: 비었던 시간에서 빠지고, 겹치는 예약도 재판매가 아니다
                cancelled(3L, at(13, 13, 0), 60, CancelType.SHOP_FAULT, 0, true, completedAt),
                confirmed(4L, at(13, 13, 0), 60, 30000, createdAt),
                // 취소 완료(2/10 12:00) 전에 생성된 예약은 다시 판 것이 아니다
                cancelled(5L, at(14, 10, 0), 60, CancelType.CUSTOMER, 0, false, LocalDateTime.of(2027, 2, 10, 12, 0)),
                confirmed(6L, at(14, 10, 0), 60, 30000, LocalDateTime.of(2027, 2, 10, 11, 0))));

        ReservationPeriodSummary summary = reservationDashboardService.getOwnerStats(OWNER, FEB_1, FEB_28).summary();

        assertThat(summary.cancelledMinutes()).isEqualTo(120);
        assertThat(summary.rebookedMinutes()).isEqualTo(30);
        assertThat(summary.rebookRate()).isEqualTo(25.0);
        assertThat(summary.rebookedAmount()).isEqualTo(30000);
    }

    @Test
    @DisplayName("대상이 없으면 비율은 null 이다")
    void summarize_noRows_ratesNull() {
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of());

        ReservationPeriodSummary summary = reservationDashboardService.getOwnerStats(OWNER, FEB_1, FEB_28).summary();

        assertThat(summary.customerCancelRate()).isNull();
        assertThat(summary.rebookRate()).isNull();
    }

    @Test
    @DisplayName("날짜별 추이는 빈 날짜를 0 으로 채우고, 시술별 성과는 금액 많은 순이다")
    void getOwnerStats_dailyAndTreatments() {
        LocalDateTime createdAt = LocalDateTime.of(2027, 1, 20, 9, 0);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                confirmed(1L, at(2, 10, 0), 60, 30000, createdAt),
                cancelled(2L, at(2, 14, 0), 60, CancelType.CUSTOMER, 0, false),
                new ReservationDashboardRow(3L, 2L, "손 케어", ReservationStatus.CONFIRMED, at(3, 10, 0),
                        at(3, 10, 30), createdAt.plusMinutes(5), createdAt, 15000, null, null, 0, false),
                new ReservationDashboardRow(4L, 2L, "손 케어", ReservationStatus.CONFIRMED, at(3, 11, 0),
                        at(3, 11, 30), createdAt.plusMinutes(5), createdAt, 18000, null, null, 0, false)));

        ReservationStatsResponse response = reservationDashboardService.getOwnerStats(
                OWNER, FEB_1, LocalDate.of(2027, 2, 3));

        assertThat(response.daily()).containsExactly(
                new ReservationStatsResponse.Daily(FEB_1, 0, 0, 0),
                new ReservationStatsResponse.Daily(LocalDate.of(2027, 2, 2), 1, 30000, 1),
                new ReservationStatsResponse.Daily(LocalDate.of(2027, 2, 3), 2, 33000, 0));
        assertThat(response.treatments()).containsExactly(
                new ReservationStatsResponse.Treatment(2L, "손 케어", 2, 33000),
                new ReservationStatsResponse.Treatment(1L, "젤네일", 1, 30000));
    }

    @Test
    @DisplayName("기간을 생략하면 이번 달, from 만 생략하면 to 의 달 1일부터, to 만 생략하면 from 의 달 말일까지다")
    void getOwnerStats_defaultPeriods() {
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of());

        ReservationPeriodSummary none = reservationDashboardService.getOwnerStats(OWNER, null, null).summary();
        ReservationPeriodSummary toOnly =
                reservationDashboardService.getOwnerStats(OWNER, null, LocalDate.of(2027, 3, 15)).summary();
        ReservationPeriodSummary fromOnly =
                reservationDashboardService.getOwnerStats(OWNER, LocalDate.of(2027, 1, 10), null).summary();

        assertThat(none.from()).isEqualTo(FEB_1);
        assertThat(none.to()).isEqualTo(FEB_28);
        assertThat(toOnly.from()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(toOnly.to()).isEqualTo(LocalDate.of(2027, 3, 15));
        assertThat(fromOnly.from()).isEqualTo(LocalDate.of(2027, 1, 10));
        assertThat(fromOnly.to()).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    @DisplayName("to 가 from 보다 앞서거나 기간이 92일을 넘으면 INVALID_INPUT 이고, 92일은 허용한다")
    void getOwnerStats_periodLimits() {
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of());
        LocalDate from = LocalDate.of(2027, 1, 1);

        assertThatThrownBy(() -> reservationDashboardService.getOwnerStats(OWNER, from, from.minusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> reservationDashboardService.getOwnerStats(OWNER, from, from.plusDays(92)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(reservationDashboardService.getOwnerStats(OWNER, from, from.plusDays(91)).daily()).hasSize(92);
    }

    @Test
    @DisplayName("매장이 아니면 FORBIDDEN 이고 조회하지 않는다")
    void customer_throwsForbidden() {
        AuthUser customer = new AuthUser(1L, UserRole.CUSTOMER, null);

        assertThatThrownBy(() -> reservationDashboardService.getOwnerDashboard(customer))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> reservationDashboardService.getOwnerStats(customer, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(reservationDashboardRepository, availabilityService);
    }

    private static ReservationDashboardRow confirmed(Long id, LocalDateTime startsAt, int minutes, int price,
            LocalDateTime createdAt) {
        return new ReservationDashboardRow(id, 1L, "젤네일", ReservationStatus.CONFIRMED, startsAt,
                startsAt.plusMinutes(minutes), createdAt.plusMinutes(5), createdAt, price, null, null, 0, false);
    }

    private static ReservationDashboardRow cancelled(Long id, LocalDateTime startsAt, int minutes, CancelType type,
            int retainedDeposit, boolean closedAfterCancel) {
        return cancelled(id, startsAt, minutes, type, retainedDeposit, closedAfterCancel, startsAt.minusDays(3));
    }

    private static ReservationDashboardRow cancelled(Long id, LocalDateTime startsAt, int minutes, CancelType type,
            int retainedDeposit, boolean closedAfterCancel, LocalDateTime completedAt) {
        LocalDateTime createdAt = startsAt.minusDays(10);
        return new ReservationDashboardRow(id, 1L, "젤네일", ReservationStatus.CANCELLED, startsAt,
                startsAt.plusMinutes(minutes), createdAt.plusMinutes(5), createdAt, 30000, type, completedAt,
                retainedDeposit, closedAfterCancel);
    }

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2027, 2, day, hour, minute);
    }

    private static OffsetDateTime kst(int hour, int minute) {
        return OffsetDateTime.of(TODAY.atTime(hour, minute), ZoneOffset.ofHours(9));
    }
}
