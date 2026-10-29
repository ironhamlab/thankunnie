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
import java.util.Map;

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
import com.ucamp.app.domain.reservation.dto.response.ReservationStatsResponse;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationDashboardCounts;
import com.ucamp.app.domain.reservation.repository.ReservationDashboardRepository;
import com.ucamp.app.domain.reservation.repository.ReservationRebookStats;
import com.ucamp.app.domain.reservation.repository.ReservationStatsRow;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

class ReservationDashboardServiceTest {

    // 2월 말일 23:59 로 고정해 월 경계(시작 포함·끝 제외)를 확인한다
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 2, 28, 23, 59);
    private static final LocalDateTime FEB_START = LocalDateTime.of(2027, 2, 1, 0, 0);
    private static final LocalDateTime MAR_START = LocalDateTime.of(2027, 3, 1, 0, 0);
    private static final AuthUser OWNER = new AuthUser(11L, UserRole.OWNER, 1L);

    private ReservationDashboardRepository reservationDashboardRepository;
    private AvailabilityService availabilityService;
    private ReservationDashboardService reservationDashboardService;

    @BeforeEach
    void setUp() {
        reservationDashboardRepository = mock(ReservationDashboardRepository.class);
        availabilityService = mock(AvailabilityService.class);
        Clock clock = Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);
        reservationDashboardService = new ReservationDashboardService(reservationDashboardRepository, availabilityService, clock);
    }

    @Test
    @DisplayName("본인 매장·이번 달·오늘 범위로 집계하고 취소 유형별 건수를 합친다")
    void getOwnerDashboard_owner_buildsMonthAndToday() {
        given(reservationDashboardRepository.countReservations(eq(1L), eq(FEB_START), eq(MAR_START),
                eq(LocalDateTime.of(2027, 2, 28, 0, 0)), eq(NOW)))
                .willReturn(new ReservationDashboardCounts(3, 1, 12, 360000L, 1));
        given(reservationDashboardRepository.countCancelledByType(1L, FEB_START, MAR_START))
                .willReturn(Map.of(CancelType.CUSTOMER, 2, CancelType.SHOP_FAULT, 1, CancelType.CUSTOMER_REQUEST, 0));
        given(reservationDashboardRepository.countRebooked(1L, FEB_START, MAR_START))
                .willReturn(new ReservationRebookStats(2, 48000L));

        ReservationDashboardResponse response = reservationDashboardService.getOwnerDashboard(OWNER);

        assertThat(response.date()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(response.today().confirmedCount()).isEqualTo(3);
        assertThat(response.today().upcomingCount()).isEqualTo(1);
        ReservationDashboardResponse.Month month = response.month();
        assertThat(month.from()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(month.to()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(month.confirmedCount()).isEqualTo(12);
        assertThat(month.expectedSales()).isEqualTo(360000L);
        assertThat(month.cancelledCount()).isEqualTo(3);
        assertThat(month.customerCancelCount()).isEqualTo(2);
        assertThat(month.shopFaultCancelCount()).isEqualTo(1);
        assertThat(month.customerRequestCancelCount()).isZero();
        assertThat(month.cancelPendingCount()).isEqualTo(1);
        assertThat(month.rebookedCount()).isEqualTo(2);
        assertThat(month.rebookedSales()).isEqualTo(48000L);
    }

    @Test
    @DisplayName("오늘 남은 빈 시간은 30분 단위 시작 시각과, 이어지는 시각을 묶은 구간으로 내려준다")
    void getOwnerDashboard_availableTimes_groupedIntoRanges() {
        LocalDate today = LocalDate.of(2027, 2, 28);
        given(reservationDashboardRepository.countReservations(any(), any(), any(), any(), any()))
                .willReturn(new ReservationDashboardCounts(0, 0, 0, 0L, 0));
        given(reservationDashboardRepository.countCancelledByType(any(), any(), any())).willReturn(Map.of());
        given(reservationDashboardRepository.countRebooked(any(), any(), any()))
                .willReturn(new ReservationRebookStats(0, 0L));
        // 15:00·15:30·16:00 은 이어지고 19:30 은 떨어져 있다
        given(availabilityService.getAvailableStartTimes(1L, today, 30)).willReturn(List.of(
                today.atTime(15, 0), today.atTime(15, 30), today.atTime(16, 0), today.atTime(19, 30)));

        ReservationDashboardResponse.Today result = reservationDashboardService.getOwnerDashboard(OWNER).today();

        assertThat(result.availableStartTimes()).containsExactly(
                kst(15, 0), kst(15, 30), kst(16, 0), kst(19, 30));
        assertThat(result.availableRanges()).containsExactly(
                new ReservationDashboardResponse.AvailableRange(kst(15, 0), kst(16, 30)),
                new ReservationDashboardResponse.AvailableRange(kst(19, 30), kst(20, 0)));
    }

    @Test
    @DisplayName("남은 빈 시간이 없으면 빈 목록이다")
    void getOwnerDashboard_noAvailableTimes_emptyLists() {
        given(reservationDashboardRepository.countReservations(any(), any(), any(), any(), any()))
                .willReturn(new ReservationDashboardCounts(0, 0, 0, 0L, 0));
        given(reservationDashboardRepository.countCancelledByType(any(), any(), any())).willReturn(Map.of());
        given(reservationDashboardRepository.countRebooked(any(), any(), any()))
                .willReturn(new ReservationRebookStats(0, 0L));
        given(availabilityService.getAvailableStartTimes(any(), any(), eq(30))).willReturn(List.of());

        ReservationDashboardResponse.Today result = reservationDashboardService.getOwnerDashboard(OWNER).today();

        assertThat(result.availableStartTimes()).isEmpty();
        assertThat(result.availableRanges()).isEmpty();
    }

    @Test
    @DisplayName("사장님이 아니면 FORBIDDEN 이고 조회하지 않는다")
    void getOwnerDashboard_customer_throwsForbidden() {
        AuthUser customer = new AuthUser(1L, UserRole.CUSTOMER, null);

        assertThatThrownBy(() -> reservationDashboardService.getOwnerDashboard(customer))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(reservationDashboardRepository, availabilityService);
    }

    @Test
    @DisplayName("집계 결과에 없는 취소 유형은 0 으로 본다")
    void getOwnerDashboard_missingCancelType_treatedAsZero() {
        given(reservationDashboardRepository.countReservations(any(), any(), any(), any(), any()))
                .willReturn(new ReservationDashboardCounts(0, 0, 0, 0L, 0));
        given(reservationDashboardRepository.countCancelledByType(any(), any(), any())).willReturn(Map.of());
        given(reservationDashboardRepository.countRebooked(any(), any(), any()))
                .willReturn(new ReservationRebookStats(0, 0L));

        ReservationDashboardResponse.Month month = reservationDashboardService.getOwnerDashboard(OWNER).month();

        assertThat(month.cancelledCount()).isZero();
        assertThat(month.shopFaultCancelCount()).isZero();
    }

    @Test
    @DisplayName("기간을 생략하면 오늘 포함 최근 30일을 조회한다")
    void getOwnerStats_noPeriod_usesLast30Days() {
        given(reservationDashboardRepository.findStatsRows(any(), any(), any())).willReturn(List.of());

        ReservationStatsResponse response = reservationDashboardService.getOwnerStats(OWNER, null, null);

        verify(reservationDashboardRepository).findStatsRows(1L, LocalDateTime.of(2027, 1, 30, 0, 0), MAR_START);
        assertThat(response.from()).isEqualTo(LocalDate.of(2027, 1, 30));
        assertThat(response.to()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(response.daily()).hasSize(30).allMatch(day -> day.confirmedCount() == 0 && day.sales() == 0);
    }

    @Test
    @DisplayName("합계·날짜별·시술별·요일 시간대별로 묶고, 점유 시점이 시작 3시간 이내면 초임박으로 센다")
    void getOwnerStats_rows_aggregates() {
        given(reservationDashboardRepository.findStatsRows(any(), any(), any())).willReturn(List.of(
                // 월 14시, 일반 구간
                row(1L, "젤네일", ReservationStatus.CONFIRMED, LocalDateTime.of(2027, 2, 1, 14, 0),
                        LocalDateTime.of(2027, 1, 20, 10, 5), 30000, false),
                // 같은 날 취소 완료
                row(2L, "손 케어", ReservationStatus.CANCELLED, LocalDateTime.of(2027, 2, 1, 11, 0),
                        LocalDateTime.of(2027, 1, 21, 9, 5), 18000, false),
                // 화 10시, 점유 시작이 정확히 3시간 전 → 초임박 (경계는 더 임박한 구간)
                row(2L, "손 케어", ReservationStatus.CONFIRMED, LocalDateTime.of(2027, 2, 2, 10, 0),
                        LocalDateTime.of(2027, 2, 2, 7, 5), 15000, false),
                // 다음 주 월 14시, 시작 2시간 전에 점유한 재판매 건
                row(1L, "젤네일", ReservationStatus.CONFIRMED, LocalDateTime.of(2027, 2, 8, 14, 0),
                        LocalDateTime.of(2027, 2, 8, 12, 5), 27000, true)));

        ReservationStatsResponse response = reservationDashboardService.getOwnerStats(
                OWNER, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 8));

        ReservationStatsResponse.Summary summary = response.summary();
        assertThat(summary.confirmedCount()).isEqualTo(3);
        assertThat(summary.sales()).isEqualTo(72000L);
        assertThat(summary.cancelledCount()).isEqualTo(1);
        assertThat(summary.rebookedCount()).isEqualTo(1);
        assertThat(summary.rebookedSales()).isEqualTo(27000L);
        assertThat(summary.lastMinuteCount()).isEqualTo(2);
        assertThat(summary.lastMinuteSales()).isEqualTo(42000L);

        assertThat(response.daily()).hasSize(8);
        assertThat(response.daily().get(0)).isEqualTo(
                new ReservationStatsResponse.Daily(LocalDate.of(2027, 2, 1), 1, 30000L, 1));
        assertThat(response.daily().get(2)).isEqualTo(
                new ReservationStatsResponse.Daily(LocalDate.of(2027, 2, 3), 0, 0L, 0));

        assertThat(response.treatments()).containsExactly(
                new ReservationStatsResponse.Treatment(1L, "젤네일", 2, 57000L, 0),
                new ReservationStatsResponse.Treatment(2L, "손 케어", 1, 15000L, 1));

        assertThat(response.hourly()).containsExactly(
                new ReservationStatsResponse.Hourly(DayOfWeek.MONDAY, 14, 2),
                new ReservationStatsResponse.Hourly(DayOfWeek.TUESDAY, 10, 1));
    }

    @Test
    @DisplayName("to 가 from 보다 앞서거나 기간이 92일을 넘으면 INVALID_INPUT 이다")
    void getOwnerStats_invalidPeriod_throwsInvalidInput() {
        LocalDate from = LocalDate.of(2027, 1, 1);

        assertThatThrownBy(() -> reservationDashboardService.getOwnerStats(OWNER, from, from.minusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> reservationDashboardService.getOwnerStats(OWNER, from, from.plusDays(92)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        verifyNoInteractions(reservationDashboardRepository);
    }

    @Test
    @DisplayName("92일 기간은 허용한다")
    void getOwnerStats_92Days_allowed() {
        given(reservationDashboardRepository.findStatsRows(any(), any(), any())).willReturn(List.of());
        LocalDate from = LocalDate.of(2027, 1, 1);

        assertThat(reservationDashboardService.getOwnerStats(OWNER, from, from.plusDays(91)).daily()).hasSize(92);
    }

    private static ReservationStatsRow row(Long treatmentId, String name, ReservationStatus status,
            LocalDateTime startsAt, LocalDateTime holdExpiresAt, int price, boolean rebooked) {
        return new ReservationStatsRow(treatmentId, name, status, startsAt, holdExpiresAt, price, rebooked);
    }

    private static OffsetDateTime kst(int hour, int minute) {
        return OffsetDateTime.of(2027, 2, 28, hour, minute, 0, 0, ZoneOffset.ofHours(9));
    }
}
