package com.ucamp.app.domain.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("사장님 대시보드는 오늘·이번 달 지표를 반환한다")
    void getDashboard_owner_returnsDashboard() throws Exception {
        ReservationDashboardResponse response = new ReservationDashboardResponse(
                LocalDate.of(2026, 11, 2),
                new ReservationDashboardResponse.Today(1, 1,
                        List.of(at(15, 0), at(15, 30)),
                        List.of(new ReservationDashboardResponse.AvailableRange(at(15, 0), at(16, 0)))),
                new ReservationDashboardResponse.Month(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                        2, 60000L, 1, 1, 0, 0, 0, 1, 15000L));
        given(reservationDashboardService.getOwnerDashboard(any(AuthUser.class))).willReturn(response);

        mockMvc.perform(get(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.today.confirmedCount").value(1))
                .andExpect(jsonPath("$.data.today.availableStartTimes[1]").value("2026-11-02T15:30:00+09:00"))
                .andExpect(jsonPath("$.data.today.availableRanges[0].endsAt").value("2026-11-02T16:00:00+09:00"))
                .andExpect(jsonPath("$.data.month.from").value("2026-11-01"))
                .andExpect(jsonPath("$.data.month.expectedSales").value(60000))
                .andExpect(jsonPath("$.data.month.rebookedCount").value(1))
                .andExpect(jsonPath("$.data.month.rebookedSales").value(15000));
    }

    @Test
    @DisplayName("비로그인 조회는 401 을 반환한다")
    void getDashboard_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationDashboardService);
    }

    @Test
    @DisplayName("기간 통계는 날짜 파라미터를 받아 합계·추이·시술별·시간대별 지표를 반환한다")
    void getStats_owner_returnsStats() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 2);
        ReservationStatsResponse response = new ReservationStatsResponse(from, to,
                new ReservationStatsResponse.Summary(2, 60000L, 1, 1, 15000L, 0, 0L),
                List.of(new ReservationStatsResponse.Daily(from, 0, 0L, 0),
                        new ReservationStatsResponse.Daily(to, 1, 30000L, 0)),
                List.of(new ReservationStatsResponse.Treatment(1L, "젤네일 원컬러", 2, 60000L, 0)),
                List.of(new ReservationStatsResponse.Hourly(DayOfWeek.MONDAY, 14, 1)));
        given(reservationDashboardService.getOwnerStats(any(AuthUser.class), eq(from), eq(to))).willReturn(response);

        mockMvc.perform(get(URL + "/stats").header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .param("from", "2026-11-01").param("to", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.sales").value(60000))
                .andExpect(jsonPath("$.data.daily.length()").value(2))
                .andExpect(jsonPath("$.data.daily[1].date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.treatments[0].treatmentName").value("젤네일 원컬러"))
                .andExpect(jsonPath("$.data.hourly[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data.hourly[0].hour").value(14));
    }

    @Test
    @DisplayName("날짜 형식이 잘못되면 400 을 반환한다")
    void getStats_invalidDate_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL + "/stats").header(StubAuthFilter.HEADER_NAME, "OWNER_1").param("from", "2026-13-01"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationDashboardService);
    }

    private static OffsetDateTime at(int hour, int minute) {
        return OffsetDateTime.of(2026, 11, 2, hour, minute, 0, 0, ZoneOffset.ofHours(9));
    }
}
