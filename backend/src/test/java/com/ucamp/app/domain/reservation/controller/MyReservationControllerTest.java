package com.ucamp.app.domain.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.reservation.dto.response.ReservationSummaryResponse;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.service.ReservationQueryService;
import com.ucamp.app.domain.reservation.service.ReservationService;

@WebMvcTest(controllers = MyReservationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class MyReservationControllerTest {

    private static final String URL = "/api/v1/my/reservations";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationQueryService reservationQueryService;

    @MockitoBean
    private ReservationService reservationService;

    @Test
    @DisplayName("목록은 날짜 필터를 받아 페이지로 반환한다")
    void getReservations_customer_returnsPage() throws Exception {
        OffsetDateTime startsAt = OffsetDateTime.of(2026, 11, 2, 14, 0, 0, 0, ZoneOffset.ofHours(9));
        ReservationSummaryResponse item = new ReservationSummaryResponse(1L, ReservationStatus.CONFIRMED,
                startsAt, startsAt.plusHours(1), 1L, "언니네 네일", 1L, "젤네일", "고객일",
                30000, 20000, 10000, "PAID", null, true);
        given(reservationQueryService.getMyReservations(any(AuthUser.class), eq(LocalDate.of(2026, 11, 1)),
                isNull(), isNull(), anyInt(), anyInt()))
                .willReturn(PageResponse.of(List.of(item), 0, 20, 1));

        mockMvc.perform(get(URL).header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1").param("from", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].shopName").value("언니네 네일"))
                .andExpect(jsonPath("$.data.content[0].cancellable").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("다른 고객 예약 상세는 404 를 반환한다")
    void getReservation_otherCustomer_returnsNotFound() throws Exception {
        given(reservationQueryService.getMyReservation(any(AuthUser.class), eq(5L)))
                .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get(URL + "/5").header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON_404"));
    }

    @Test
    @DisplayName("비로그인 조회는 401 을 반환한다")
    void getReservations_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationQueryService);
    }

    @Test
    @DisplayName("현재 점유가 없으면 data 가 null 인 200 을 반환한다")
    void getCurrentHold_none_returnsNullData() throws Exception {
        mockMvc.perform(get(URL + "/current-hold").header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(reservationQueryService).getMyCurrentHold(any(AuthUser.class));
    }

    @Test
    @DisplayName("점유 풀기는 본문 없이 204 를 반환한다")
    void releaseHold_customer_returnsNoContent() throws Exception {
        mockMvc.perform(post(URL + "/5/release").header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(reservationService).releaseHold(any(AuthUser.class), eq(5L));
    }

    @Test
    @DisplayName("이미 확정된 예약의 점유 풀기는 409 를 반환한다")
    void releaseHold_confirmed_returnsConflict() throws Exception {
        willThrow(new BusinessException(ErrorCode.RESERVATION_ALREADY_CONFIRMED))
                .given(reservationService).releaseHold(any(AuthUser.class), eq(5L));

        mockMvc.perform(post(URL + "/5/release").header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESERVATION_002"));
    }

    @Test
    @DisplayName("비로그인 점유 풀기는 401 을 반환한다")
    void releaseHold_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(post(URL + "/5/release")).andExpect(status().isUnauthorized());
        verifyNoInteractions(reservationService);
    }
}
