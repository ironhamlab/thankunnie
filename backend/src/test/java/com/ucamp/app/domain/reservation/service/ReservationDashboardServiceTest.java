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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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
import com.ucamp.app.domain.reservation.dto.response.ReservationStatsResponse.Granularity;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationDashboardRepository;
import com.ucamp.app.domain.reservation.repository.ReservationDashboardRow;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

class ReservationDashboardServiceTest {

    // 2027-02-10 은 수요일
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
    @DisplayName("홈 대시보드는 오늘·내일 예약, 남은 빈 시간대, 이번 달 누적과 지난달 같은 기간, 찜 고객 수를 내려준다")
    void getOwnerDashboard_owner_buildsTodayTomorrowAndMonthToDate() {
        LocalDateTime createdAt = LocalDateTime.of(2027, 1, 5, 9, 0);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                // 지난달 같은 기간(1/1~1/10) 안 → 비교 금액
                confirmed(1L, 1L, LocalDateTime.of(2027, 1, 8, 14, 0), 60, 20000, createdAt),
                // 지난달 같은 기간 밖 → 비교에서 빠진다
                confirmed(2L, 1L, LocalDateTime.of(2027, 1, 20, 14, 0), 60, 99000, createdAt),
                // 이번 달 끝난 예약 → 이번 달 누적
                confirmed(3L, 1L, LocalDateTime.of(2027, 2, 3, 10, 0), 60, 30000, createdAt),
                confirmed(4L, 2L, TODAY.atTime(10, 0), 60, 30000, createdAt),
                // 오늘이지만 아직 끝나지 않음 → 누적에서 빠지고 남은 예약으로 센다
                confirmed(5L, 2L, TODAY.atTime(15, 0), 60, 20000, createdAt),
                confirmed(6L, 3L, LocalDateTime.of(2027, 2, 11, 14, 0), 60, 25000, createdAt),
                cancelled(7L, LocalDateTime.of(2027, 2, 11, 16, 0), 60, CancelType.CUSTOMER, 0, false)));
        // 13:00·13:30 은 이어지고 16:00 은 떨어져 있다
        given(availabilityService.getAvailableStartTimes(1L, TODAY, 30)).willReturn(List.of(
                TODAY.atTime(13, 0), TODAY.atTime(13, 30), TODAY.atTime(16, 0)));
        given(availabilityService.hasOpenTime(1L, TODAY)).willReturn(true);
        given(reservationDashboardRepository.countFavoriteCustomers(1L)).willReturn(37L);

        ReservationDashboardResponse response = reservationDashboardService.getOwnerDashboard(OWNER);

        // 지난달 1일부터 내일까지 읽는다
        verify(reservationDashboardRepository).findRows(1L, LocalDateTime.of(2027, 1, 1, 0, 0),
                LocalDateTime.of(2027, 2, 12, 0, 0));
        assertThat(response.date()).isEqualTo(TODAY);
        ReservationDashboardResponse.Today today = response.today();
        assertThat(today.confirmedCount()).isEqualTo(2);
        assertThat(today.upcomingCount()).isEqualTo(1);
        assertThat(today.closed()).isFalse();
        assertThat(today.availableMinutes()).isEqualTo(90);
        assertThat(today.availableRanges()).containsExactly(
                new ReservationDashboardResponse.AvailableRange(kst(13, 0), kst(14, 0)),
                new ReservationDashboardResponse.AvailableRange(kst(16, 0), kst(16, 30)));
        assertThat(response.tomorrowConfirmedCount()).isEqualTo(1);
        assertThat(response.monthToDate()).isEqualTo(new ReservationDashboardResponse.MonthToDate(
                FEB_1, TODAY, 60000, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 10), 20000, 200.0));
        assertThat(response.favoriteCustomerCount()).isEqualTo(37);
    }

    @Test
    @DisplayName("지난달이 더 짧으면 지난달 말일까지 비교하고, 지난달 금액이 0 이면 증감률은 null 이다")
    void getOwnerDashboard_shortPreviousMonth_clampsAndNullRate() {
        Clock clock = Clock.fixed(LocalDateTime.of(2027, 3, 31, 12, 0).atZone(TimeUtils.KST).toInstant(),
                TimeUtils.KST);
        ReservationDashboardService service =
                new ReservationDashboardService(reservationDashboardRepository, availabilityService, clock);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of());

        ReservationDashboardResponse.MonthToDate monthToDate = service.getOwnerDashboard(OWNER).monthToDate();

        assertThat(monthToDate.previousFrom()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(monthToDate.previousTo()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(monthToDate.changeRate()).isNull();
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
    @DisplayName("요약은 이용 추정과 예정을 나누고, 객단가·취소·환불되지 않은 예약금·초임박을 센다")
    void summarize_splitsCompletedAndUpcoming() {
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                confirmed(1L, 1L, at(3, 10, 0), 60, 30000, LocalDateTime.of(2027, 1, 20, 9, 0)),
                // 점유 시각이 정확히 시작 3시간 전 → 초임박 (경계는 더 임박한 구간)
                confirmed(2L, 2L, at(4, 10, 0), 60, 20000, at(4, 7, 0)),
                // 오늘 11:30~12:30 → 아직 끝나지 않아 예정
                confirmed(3L, 3L, at(10, 11, 30), 60, 25000, LocalDateTime.of(2027, 1, 20, 9, 0)),
                confirmed(4L, 3L, at(20, 10, 0), 60, 25000, LocalDateTime.of(2027, 1, 20, 9, 0)),
                cancelled(5L, at(5, 10, 0), 60, CancelType.CUSTOMER, 20000, false),
                cancelled(6L, at(6, 10, 0), 60, CancelType.CUSTOMER_REQUEST, 15000, false),
                cancelled(7L, at(7, 10, 0), 60, CancelType.SHOP_FAULT, 0, true),
                // 기간 밖
                confirmed(8L, 1L, LocalDateTime.of(2027, 3, 1, 10, 0), 60, 99000, LocalDateTime.of(2027, 2, 1, 9, 0))));

        ReservationPeriodSummary summary = stats(FEB_1, FEB_28).summary();

        assertThat(summary.completedCount()).isEqualTo(2);
        assertThat(summary.completedAmount()).isEqualTo(50000);
        assertThat(summary.upcomingCount()).isEqualTo(2);
        assertThat(summary.upcomingAmount()).isEqualTo(50000);
        assertThat(summary.averageAmount()).isEqualTo(25000L);
        assertThat(summary.customerCancelCount()).isEqualTo(2);
        assertThat(summary.shopFaultCancelCount()).isEqualTo(1);
        // 고객 취소 2 ÷ (확정 4 + 고객 취소 2 + 매장 사유 1)
        assertThat(summary.customerCancelRate()).isEqualTo(28.6);
        assertThat(summary.nonRefundedDepositAmount()).isEqualTo(35000);
        assertThat(summary.lastMinuteCount()).isEqualTo(1);
        assertThat(summary.lastMinuteAmount()).isEqualTo(20000);
        // 닫은 매장 사유 취소는 비었던 시간에서 뺀다
        assertThat(summary.cancelledMinutes()).isEqualTo(120);
        assertThat(summary.rebookedMinutes()).isZero();
        assertThat(summary.rebookRate()).isEqualTo(0.0);
        assertThat(summary.rebookedBookingAmount()).isZero();
    }

    @Test
    @DisplayName("90분 취소 2건 자리에 60분 예약 3건이 들어오면 비었던 3시간을 모두 다시 채운 것이다")
    void summarize_rebookByTime_fullyRefilled() {
        LocalDateTime completedAt = LocalDateTime.of(2027, 2, 8, 9, 0);
        LocalDateTime createdAt = LocalDateTime.of(2027, 2, 9, 9, 0);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                cancelled(1L, at(12, 13, 0), 90, CancelType.CUSTOMER, 0, false, completedAt),
                cancelled(2L, at(12, 14, 30), 90, CancelType.CUSTOMER, 0, false, completedAt),
                confirmed(3L, 1L, at(12, 13, 0), 60, 20000, createdAt),
                confirmed(4L, 1L, at(12, 14, 0), 60, 20000, createdAt),
                confirmed(5L, 1L, at(12, 15, 0), 60, 20000, createdAt)));

        ReservationPeriodSummary summary = stats(FEB_1, FEB_28).summary();

        assertThat(summary.cancelledMinutes()).isEqualTo(180);
        assertThat(summary.rebookedMinutes()).isEqualTo(180);
        assertThat(summary.rebookRate()).isEqualTo(100.0);
        assertThat(summary.rebookedBookingAmount()).isEqualTo(60000);
    }

    @Test
    @DisplayName("겹친 부분만 채운 시간으로 세고, 취소 완료 전 생성 예약·닫은 시간·다른 날 예약은 빼며, 예약 금액은 한 번만 센다")
    void summarize_rebookByTime_partialAndExclusions() {
        LocalDateTime completedAt = LocalDateTime.of(2027, 2, 8, 9, 0);
        LocalDateTime createdAt = LocalDateTime.of(2027, 2, 9, 9, 0);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                // 13:00~14:00 취소 → 13:30~14:30 예약이 30분만 채움
                cancelled(1L, at(12, 13, 0), 60, CancelType.CUSTOMER, 0, false, completedAt),
                confirmed(2L, 1L, at(12, 13, 30), 60, 30000, createdAt),
                // 다음 날 같은 시각 예약은 겹치지 않는다
                confirmed(9L, 1L, at(13, 13, 0), 60, 30000, createdAt),
                // 매장이 취소하며 닫은 시간: 비었던 시간에서 빠지고, 겹치는 예약도 재판매가 아니다
                cancelled(3L, at(14, 13, 0), 60, CancelType.SHOP_FAULT, 0, true, completedAt),
                confirmed(4L, 1L, at(14, 13, 0), 60, 30000, createdAt),
                // 취소 완료(2/10 12:00) 전에 생성된 예약은 다시 판 것이 아니다
                cancelled(5L, at(15, 10, 0), 60, CancelType.CUSTOMER, 0, false, LocalDateTime.of(2027, 2, 10, 12, 0)),
                confirmed(6L, 1L, at(15, 10, 0), 60, 30000, LocalDateTime.of(2027, 2, 10, 11, 0))));

        ReservationPeriodSummary summary = stats(FEB_1, FEB_28).summary();

        assertThat(summary.cancelledMinutes()).isEqualTo(120);
        assertThat(summary.rebookedMinutes()).isEqualTo(30);
        assertThat(summary.rebookRate()).isEqualTo(25.0);
        assertThat(summary.rebookedBookingAmount()).isEqualTo(30000);
    }

    @Test
    @DisplayName("대상이 없으면 비율과 객단가는 null 이다")
    void summarize_noRows_ratesNull() {
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of());

        ReservationPeriodSummary summary = stats(FEB_1, FEB_28).summary();

        assertThat(summary.customerCancelRate()).isNull();
        assertThat(summary.rebookRate()).isNull();
        assertThat(summary.revisitRate()).isNull();
        assertThat(summary.averageAmount()).isNull();
    }

    @Test
    @DisplayName("신규·재방문은 기간 끝 시점까지 매장 전체 이용 횟수로 나누고, 예정 예약만 있는 고객은 이용 고객이 아니다")
    void summarize_newAndReturningByWholeHistory() {
        LocalDateTime createdAt = LocalDateTime.of(2027, 1, 20, 9, 0);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                // 고객 1: 기간 안 1회 + 기간 전 1회 → 재방문
                confirmed(1L, 1L, at(3, 10, 0), 60, 30000, createdAt),
                // 고객 2: 기간 안 2회 → 재방문
                confirmed(2L, 2L, at(4, 10, 0), 60, 30000, createdAt),
                confirmed(3L, 2L, at(5, 10, 0), 60, 30000, createdAt),
                // 고객 3: 기간 안 처음 1회 → 신규
                confirmed(4L, 3L, at(6, 10, 0), 60, 30000, createdAt),
                // 고객 4: 예정만 있음 → 세지 않는다
                confirmed(5L, 4L, at(20, 10, 0), 60, 30000, createdAt)));
        given(reservationDashboardRepository.findVisits(1L, NOW)).willReturn(List.of(
                visit(1L, LocalDateTime.of(2027, 1, 3, 11, 0)),
                visit(1L, at(3, 11, 0)),
                visit(2L, at(4, 11, 0)),
                visit(2L, at(5, 11, 0)),
                visit(3L, at(6, 11, 0))));

        ReservationPeriodSummary summary = stats(FEB_1, FEB_28).summary();

        assertThat(summary.customerCount()).isEqualTo(3);
        assertThat(summary.newCustomerCount()).isEqualTo(1);
        assertThat(summary.returningCustomerCount()).isEqualTo(2);
        assertThat(summary.revisitRate()).isEqualTo(66.7);
    }

    @Test
    @DisplayName("62일 이하는 날짜별로 빈 날도 0 으로 채운다")
    void getOwnerStats_shortPeriod_dailyTrend() {
        LocalDateTime createdAt = LocalDateTime.of(2027, 1, 20, 9, 0);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                confirmed(1L, 1L, at(2, 10, 0), 60, 30000, createdAt),
                cancelled(2L, at(2, 14, 0), 60, CancelType.CUSTOMER, 0, false),
                confirmed(3L, 1L, at(3, 10, 0), 30, 15000, createdAt)));

        ReservationStatsResponse response = stats(FEB_1, LocalDate.of(2027, 2, 3));

        assertThat(response.granularity()).isEqualTo(Granularity.DAY);
        assertThat(response.trend()).containsExactly(
                new ReservationStatsResponse.Trend(FEB_1, FEB_1, 0, 0, 0, 0, 0),
                new ReservationStatsResponse.Trend(at(2, 0, 0).toLocalDate(), at(2, 0, 0).toLocalDate(),
                        1, 30000, 0, 0, 1),
                new ReservationStatsResponse.Trend(at(3, 0, 0).toLocalDate(), at(3, 0, 0).toLocalDate(),
                        1, 15000, 0, 0, 0));
        assertThat(stats(FEB_1, FEB_1.plusDays(61)).granularity()).isEqualTo(Granularity.DAY);
        assertThat(stats(FEB_1, FEB_1.plusDays(62)).granularity()).isEqualTo(Granularity.MONTH);
    }

    @Test
    @DisplayName("62일을 넘으면 월별로 묶고, 첫·마지막 달은 조회 기간에 걸친 날짜만 담는다")
    void getOwnerStats_longPeriod_monthlyTrend() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 1, 9, 0);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                confirmed(1L, 1L, LocalDateTime.of(2026, 11, 20, 10, 0), 60, 30000, createdAt),
                confirmed(2L, 1L, LocalDateTime.of(2026, 12, 5, 10, 0), 60, 20000, createdAt),
                confirmed(3L, 2L, LocalDateTime.of(2026, 12, 6, 10, 0), 60, 20000, createdAt),
                // 오늘 오후 → 예정
                confirmed(4L, 2L, TODAY.atTime(15, 0), 60, 25000, createdAt)));

        ReservationStatsResponse response = stats(LocalDate.of(2026, 11, 15), TODAY);

        assertThat(response.granularity()).isEqualTo(Granularity.MONTH);
        assertThat(response.trend()).containsExactly(
                new ReservationStatsResponse.Trend(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 30),
                        1, 30000, 0, 0, 0),
                new ReservationStatsResponse.Trend(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31),
                        2, 40000, 0, 0, 0),
                new ReservationStatsResponse.Trend(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31),
                        0, 0, 0, 0, 0),
                new ReservationStatsResponse.Trend(FEB_1, TODAY, 0, 0, 1, 25000, 0));
    }

    @Test
    @DisplayName("요일별은 월~일 7개, 시간대별은 예약 시간을 정시로 나눠 더하고, 시술별은 금액 비중을 붙인다")
    void getOwnerStats_weekdayHeatmapTreatments() {
        LocalDateTime createdAt = LocalDateTime.of(2027, 1, 20, 9, 0);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                // 2/6 토요일 13:30~15:00 → 13시 30분, 14시 60분
                confirmed(1L, 1L, at(6, 13, 30), 90, 30000, createdAt),
                // 2/13 토요일 14:00~15:00 → 14시 60분
                new ReservationDashboardRow(2L, 2L, 2L, "손 케어", ReservationStatus.CONFIRMED, at(13, 14, 0),
                        at(13, 15, 0), createdAt.plusMinutes(5), createdAt, 10000, null, null, 0, false),
                // 취소는 요일·시간대·시술에 넣지 않는다
                cancelled(3L, at(8, 10, 0), 60, CancelType.CUSTOMER, 0, false)));

        ReservationStatsResponse response = stats(FEB_1, FEB_28);

        assertThat(response.byWeekday()).hasSize(7);
        assertThat(response.byWeekday().get(0)).isEqualTo(
                new ReservationStatsResponse.Weekday(DayOfWeek.MONDAY, 0, 0));
        assertThat(response.byWeekday().get(5)).isEqualTo(
                new ReservationStatsResponse.Weekday(DayOfWeek.SATURDAY, 2, 40000));
        assertThat(response.heatmap()).containsExactly(
                new ReservationStatsResponse.HeatmapCell(DayOfWeek.SATURDAY, 13, 30),
                new ReservationStatsResponse.HeatmapCell(DayOfWeek.SATURDAY, 14, 120));
        assertThat(response.treatments()).containsExactly(
                new ReservationStatsResponse.Treatment(1L, "젤네일", 1, 30000, 75.0),
                new ReservationStatsResponse.Treatment(2L, "손 케어", 1, 10000, 25.0));
    }

    @Test
    @DisplayName("직전 같은 길이 기간을 함께 요약하고, 그 기간이 서비스 이용 시작 전이면 null 이다")
    void getOwnerStats_previousPeriod() {
        given(reservationDashboardRepository.findServiceStart(1L))
                .willReturn(Optional.of(serviceStart(LocalDateTime.of(2027, 1, 1, 9, 0), null)));
        LocalDateTime createdAt = LocalDateTime.of(2027, 1, 2, 9, 0);
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of(
                confirmed(1L, 1L, LocalDateTime.of(2027, 1, 25, 10, 0), 60, 20000, createdAt),
                confirmed(2L, 1L, at(3, 10, 0), 60, 30000, createdAt)));

        // 2/1~2/7 의 직전 기간은 1/25~1/31
        ReservationStatsResponse week = stats(FEB_1, LocalDate.of(2027, 2, 7));
        // 1/1~1/31 의 직전 기간 12/1~12/31 은 서비스 이용 전
        ReservationStatsResponse january = stats(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31));

        verify(reservationDashboardRepository).findRows(1L, LocalDateTime.of(2027, 1, 24, 0, 0),
                LocalDateTime.of(2027, 2, 9, 0, 0));
        assertThat(week.previous().from()).isEqualTo(LocalDate.of(2027, 1, 25));
        assertThat(week.previous().to()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(week.previous().completedAmount()).isEqualTo(20000);
        assertThat(week.summary().completedAmount()).isEqualTo(30000);
        assertThat(january.previous()).isNull();
    }

    @Test
    @DisplayName("all 이면 매장 가입일과 첫 예약일 중 이른 날부터 오늘까지다")
    void getOwnerStats_all_fromServiceStartToToday() {
        given(reservationDashboardRepository.findServiceStart(1L)).willReturn(Optional.of(
                serviceStart(LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2026, 9, 15, 10, 0))));
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of());

        ReservationStatsResponse response = reservationDashboardService.getOwnerStats(OWNER, FEB_1, FEB_28, true);

        assertThat(response.serviceStartDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(response.summary().from()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(response.summary().to()).isEqualTo(TODAY);
        assertThat(response.previous()).isNull();
    }

    @Test
    @DisplayName("기간을 생략하면 이번 달, from 만 생략하면 to 의 달 1일부터, to 만 생략하면 from 의 달 말일까지다")
    void getOwnerStats_defaultPeriods() {
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of());

        ReservationPeriodSummary none = stats(null, null).summary();
        ReservationPeriodSummary toOnly = stats(null, LocalDate.of(2027, 3, 15)).summary();
        ReservationPeriodSummary fromOnly = stats(LocalDate.of(2027, 1, 10), null).summary();

        assertThat(none.from()).isEqualTo(FEB_1);
        assertThat(none.to()).isEqualTo(FEB_28);
        assertThat(toOnly.from()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(toOnly.to()).isEqualTo(LocalDate.of(2027, 3, 15));
        assertThat(fromOnly.from()).isEqualTo(LocalDate.of(2027, 1, 10));
        assertThat(fromOnly.to()).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    @DisplayName("to 가 from 보다 앞서거나 기간이 3660일을 넘으면 INVALID_INPUT 이고, 1년은 허용한다")
    void getOwnerStats_periodLimits() {
        given(reservationDashboardRepository.findRows(any(), any(), any())).willReturn(List.of());
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> stats(from, from.minusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> stats(from.minusDays(3660), from))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(stats(from, from.plusYears(1).minusDays(1)).trend()).hasSize(12);
    }

    @Test
    @DisplayName("매장이 아니면 FORBIDDEN 이고 조회하지 않는다")
    void customer_throwsForbidden() {
        AuthUser customer = new AuthUser(1L, UserRole.CUSTOMER, null);

        assertThatThrownBy(() -> reservationDashboardService.getOwnerDashboard(customer))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> reservationDashboardService.getOwnerStats(customer, null, null, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(reservationDashboardRepository, availabilityService);
    }

    private ReservationStatsResponse stats(LocalDate from, LocalDate to) {
        return reservationDashboardService.getOwnerStats(OWNER, from, to, false);
    }

    private static ReservationDashboardRow confirmed(Long id, Long userId, LocalDateTime startsAt, int minutes,
            int price, LocalDateTime createdAt) {
        return new ReservationDashboardRow(id, userId, 1L, "젤네일", ReservationStatus.CONFIRMED, startsAt,
                startsAt.plusMinutes(minutes), createdAt.plusMinutes(5), createdAt, price, null, null, 0, false);
    }

    private static ReservationDashboardRow cancelled(Long id, LocalDateTime startsAt, int minutes, CancelType type,
            int retainedDeposit, boolean closedAfterCancel) {
        return cancelled(id, startsAt, minutes, type, retainedDeposit, closedAfterCancel, startsAt.minusDays(3));
    }

    private static ReservationDashboardRow cancelled(Long id, LocalDateTime startsAt, int minutes, CancelType type,
            int retainedDeposit, boolean closedAfterCancel, LocalDateTime completedAt) {
        LocalDateTime createdAt = startsAt.minusDays(10);
        return new ReservationDashboardRow(id, 9L, 1L, "젤네일", ReservationStatus.CANCELLED, startsAt,
                startsAt.plusMinutes(minutes), createdAt.plusMinutes(5), createdAt, 30000, type, completedAt,
                retainedDeposit, closedAfterCancel);
    }

    private static ReservationDashboardRepository.Visit visit(Long userId, LocalDateTime endsAt) {
        return new ReservationDashboardRepository.Visit() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public LocalDateTime getEndsAt() {
                return endsAt;
            }
        };
    }

    private static ReservationDashboardRepository.ServiceStart serviceStart(LocalDateTime shopCreatedAt,
            LocalDateTime firstStartsAt) {
        return new ReservationDashboardRepository.ServiceStart() {
            @Override
            public LocalDateTime getShopCreatedAt() {
                return shopCreatedAt;
            }

            @Override
            public LocalDateTime getFirstStartsAt() {
                return firstStartsAt;
            }
        };
    }

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2027, 2, day, hour, minute);
    }

    private static OffsetDateTime kst(int hour, int minute) {
        return OffsetDateTime.of(TODAY.atTime(hour, minute), ZoneOffset.ofHours(9));
    }
}
