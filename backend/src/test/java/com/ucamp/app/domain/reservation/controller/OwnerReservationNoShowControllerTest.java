package com.ucamp.app.domain.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.reservation.dto.response.ReservationDetailResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationSummaryResponse;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.service.ReservationNoShowService;
import com.ucamp.app.domain.reservation.service.ReservationQueryService;

@WebMvcTest(controllers = OwnerReservationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class OwnerReservationNoShowControllerTest {

    private static final String URL = "/api/v1/owner/reservations/1/no-show";
    private static final OffsetDateTime AT = OffsetDateTime.of(2026, 11, 2, 14, 0, 0, 0, ZoneOffset.ofHours(9));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationQueryService reservationQueryService;

    @MockitoBean
    private ReservationNoShowService reservationNoShowService;

    @Test
    @DisplayName("노쇼로 표시하면 noShow 가 true 인 예약 상세를 반환한다")
    void markNoShow_owner_returnsDetail() throws Exception {
        given(reservationNoShowService.markNoShow(any(AuthUser.class), eq(1L))).willReturn(detail(true));

        mockMvc.perform(put(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.noShow").value(true))
                .andExpect(jsonPath("$.data.summary.customerId").value(1));
    }

    @Test
    @DisplayName("노쇼를 해제하면 noShow 가 false 인 예약 상세를 반환한다")
    void unmarkNoShow_owner_returnsDetail() throws Exception {
        given(reservationNoShowService.unmarkNoShow(any(AuthUser.class), eq(1L))).willReturn(detail(false));

        mockMvc.perform(delete(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.noShow").value(false));
    }

    @Test
    @DisplayName("끝나지 않은 예약을 노쇼로 표시하면 409 RESERVATION_009 를 반환한다")
    void markNoShow_notEnded_returnsConflict() throws Exception {
        given(reservationNoShowService.markNoShow(any(AuthUser.class), eq(1L)))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_NOT_ENDED));

        mockMvc.perform(put(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESERVATION_009"));
    }

    @Test
    @DisplayName("비로그인은 401 이다")
    void anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(put(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationNoShowService);
    }

    private static ReservationDetailResponse detail(boolean noShow) {
        ReservationSummaryResponse summary = new ReservationSummaryResponse(1L, ReservationStatus.CONFIRMED,
                AT, AT.plusHours(1), 1L, "언니네 네일", 1L, "젤네일", "고객일",
                30000, 20000, 10000, "PAID", null, false, 1L, noShow);
        return new ReservationDetailResponse(summary, "010-1111-1111", null, AT.minusDays(3), AT.minusDays(3),
                AT.minusDays(3), null, null, AT.plusHours(2));
    }
}
