package com.ucamp.app.domain.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.reservation.dto.response.ReservationSummaryResponse;
import com.ucamp.app.domain.reservation.entity.ReservationDisplayStatus;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.service.ReservationNoShowService;
import com.ucamp.app.domain.reservation.service.ReservationQueryService;

@WebMvcTest(controllers = OwnerReservationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class OwnerReservationControllerTest {

    private static final String URL = "/api/v1/owner/reservations";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationQueryService reservationQueryService;

    // 노쇼 API 는 OwnerReservationNoShowControllerTest 에서 검증한다
    @MockitoBean
    private ReservationNoShowService reservationNoShowService;

    @Test
    @DisplayName("목록은 날짜·상태 필터를 받아 페이지로 반환한다")
    void getReservations_owner_returnsPage() throws Exception {
        OffsetDateTime startsAt = OffsetDateTime.of(2026, 11, 2, 14, 0, 0, 0, ZoneOffset.ofHours(9));
        ReservationSummaryResponse item = new ReservationSummaryResponse(1L, ReservationStatus.CONFIRMED,
                startsAt, startsAt.plusHours(1), 1L, "언니네 네일", 1L, "젤네일", "고객일",
                30000, 20000, 10000, "PAID", null, true, 1L, false, ReservationDisplayStatus.CONFIRMED);
        given(reservationQueryService.getOwnerReservations(any(AuthUser.class), eq(LocalDate.of(2026, 11, 2)),
                eq(LocalDate.of(2026, 11, 3)), eq(List.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED)),
                anyInt(), anyInt()))
                .willReturn(PageResponse.of(List.of(item), 0, 20, 1));

        mockMvc.perform(get(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .param("from", "2026-11-02").param("to", "2026-11-03")
                        .param("status", "CONFIRMED", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reservationId").value(1))
                .andExpect(jsonPath("$.data.content[0].startsAt").value("2026-11-02T14:00:00+09:00"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("없는 상태 값이면 400 을 반환한다")
    void getReservations_unknownStatus_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1").param("status", "DONE"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservationQueryService);
    }

    @Test
    @DisplayName("비로그인 조회는 401 을 반환한다")
    void getReservations_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationQueryService);
    }
}
