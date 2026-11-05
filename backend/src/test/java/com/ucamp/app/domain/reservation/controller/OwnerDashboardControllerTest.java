package com.ucamp.app.domain.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.StubAuthFilter;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.reservation.dto.response.ReservationDashboardResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationPeriodSummary;
import com.ucamp.app.domain.reservation.dto.response.ReservationStatsResponse;
import com.ucamp.app.domain.reservation.service.ReservationDashboardService;

@WebMvcTest(controllers = OwnerDashboardController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class OwnerDashboardControllerTest {

    private static final String URL = "/api/v1/owner/dashboard";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationDashboardService reservationDashboardService;

    @Test
    @DisplayName("홈 대시보드는 오늘·내일 지표, 이번 달 누적 비교, 찜 고객 수를 반환한다")
    void getDashboard_owner_returnsDashboard() throws Exception {
        ReservationDashboardResponse response = new ReservationDashboardResponse(
                LocalDate.of(2026, 11, 2),
                new ReservationDashboardResponse.Today(1, 1, false, 60,
                        List.of(new ReservationDashboardResponse.AvailableRange(at(15, 0), at(16, 0)))),
                3,
                new ReservationDashboardResponse.MonthToDate(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2),
                        60000, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), 50000, 20.0),
                37);
        given(reservationDashboardService.getOwnerDashboard(any(AuthUser.class))).willReturn(response);

        mockMvc.perform(get(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.today.closed").value(false))
                .andExpect(jsonPath("$.data.today.availableMinutes").value(60))
                .andExpect(jsonPath("$.data.today.availableRanges[0].endsAt").value("2026-11-02T16:00:00+09:00"))
                .andExpect(jsonPath("$.data.tomorrowConfirmedCount").value(3))
                .andExpect(jsonPath("$.data.monthToDate.completedAmount").value(60000))
                .andExpect(jsonPath("$.data.monthToDate.previousTo").value("2026-10-02"))
                .andExpect(jsonPath("$.data.monthToDate.changeRate").value(20.0))
                .andExpect(jsonPath("$.data.favoriteCustomerCount").value(37));
    }

    @Test
    @DisplayName("비로그인 조회는 401 을 반환한다")
    void getDashboard_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationDashboardService);
    }

    @Test
    @DisplayName("분석은 날짜 파라미터를 받아 요약·직전 기간·추이·요일·시간대·시술 지표를 반환한다")
    void getStats_owner_returnsStats() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 2);
        ReservationStatsResponse response = new ReservationStatsResponse(
                ReservationStatsResponse.Granularity.DAY,
                LocalDate.of(2026, 10, 1),
                summary(from, to),
                summary(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31)),
                List.of(new ReservationStatsResponse.Trend(from, from, 0, 0, 0, 0, 0),
                        new ReservationStatsResponse.Trend(to, to, 1, 30000, 0, 0, 0)),
                List.of(new ReservationStatsResponse.Weekday(DayOfWeek.MONDAY, 1, 30000, 4)),
                List.of(new ReservationStatsResponse.HeatmapCell(DayOfWeek.MONDAY, 14, 60, 240)),
                List.of(new ReservationStatsResponse.Treatment(1L, "젤네일 원컬러", 2, 60000L, 100.0)));
        given(reservationDashboardService.getOwnerStats(any(AuthUser.class), eq(from), eq(to), eq(false)))
                .willReturn(response);

        mockMvc.perform(get(URL + "/stats").header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .param("from", "2026-11-01").param("to", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.granularity").value("DAY"))
                .andExpect(jsonPath("$.data.serviceStartDate").value("2026-10-01"))
                .andExpect(jsonPath("$.data.summary.to").value("2026-11-02"))
                .andExpect(jsonPath("$.data.summary.nonRefundedDepositAmount").value(20000))
                .andExpect(jsonPath("$.data.previous.from").value("2026-10-30"))
                .andExpect(jsonPath("$.data.trend.length()").value(2))
                .andExpect(jsonPath("$.data.trend[1].completedAmount").value(30000))
                .andExpect(jsonPath("$.data.byWeekday[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data.byWeekday[0].openDays").value(4))
                .andExpect(jsonPath("$.data.heatmap[0].bookedMinutes").value(60))
                .andExpect(jsonPath("$.data.heatmap[0].openMinutes").value(240))
                .andExpect(jsonPath("$.data.treatments[0].share").value(100.0));
    }

    @Test
    @DisplayName("all=true 는 전체 기간 조회로 넘긴다")
    void getStats_all_passesFlag() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 11, 2);
        given(reservationDashboardService.getOwnerStats(any(AuthUser.class), isNull(), isNull(), eq(true)))
                .willReturn(new ReservationStatsResponse(ReservationStatsResponse.Granularity.DAY, from,
                        summary(from, to), null, List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get(URL + "/stats").header(StubAuthFilter.HEADER_NAME, "OWNER_1").param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.from").value("2026-10-01"))
                .andExpect(jsonPath("$.data.previous").doesNotExist());
    }

    @Test
    @DisplayName("날짜 형식이 잘못되면 400 을 반환한다")
    void getStats_invalidDate_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL + "/stats").header(StubAuthFilter.HEADER_NAME, "OWNER_1").param("from", "2026-13-01"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationDashboardService);
    }

    private static ReservationPeriodSummary summary(LocalDate from, LocalDate to) {
        return new ReservationPeriodSummary(from, to, 2, 60000L, 0, 0L, 30000L, 1, 1, 25.0, 20000L,
                0L, 0L, null, 0L, 0, 0L, 2, 1, 1, 50.0, 0, null);
    }

    private static OffsetDateTime at(int hour, int minute) {
        return OffsetDateTime.of(2026, 11, 2, hour, minute, 0, 0, ZoneOffset.ofHours(9));
    }
}
