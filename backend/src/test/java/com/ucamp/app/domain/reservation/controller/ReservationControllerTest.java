package com.ucamp.app.domain.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.common.auth.StubAuthFilter;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.reservation.dto.response.ReservationCreateResponse;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.service.ReservationService;

@WebMvcTest(controllers = ReservationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ReservationControllerTest {

    private static final String URL = "/api/v1/reservations";
    private static final String VALID_BODY = """
            {"treatmentId": 1, "startsAt": "2026-11-01T20:00:00+09:00"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @Test
    @DisplayName("고객이 예약하면 201 과 점유 예약 정보를 반환한다")
    void createReservation_customer_returnsCreated() throws Exception {
        ReservationCreateResponse response = new ReservationCreateResponse(10L, 1L, ReservationStatus.HOLD,
                OffsetDateTime.parse("2026-11-01T20:00:00+09:00"), OffsetDateTime.parse("2026-11-01T21:00:00+09:00"),
                30_000, 20_000, OffsetDateTime.parse("2026-11-01T09:05:00+09:00"));
        given(reservationService.createReservation(eq(TestUser.CUSTOMER_1.toAuthUser()), any()))
                .willReturn(response);

        mockMvc.perform(post(URL)
                        .header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").value(10))
                .andExpect(jsonPath("$.data.status").value("HOLD"))
                .andExpect(jsonPath("$.data.startsAt").value("2026-11-01T20:00:00+09:00"))
                .andExpect(jsonPath("$.data.appliedPrice").value(30000))
                .andExpect(jsonPath("$.data.deposit").value(20000));
    }

    @Test
    @DisplayName("비로그인이면 401 을 반환한다")
    void createReservation_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verifyNoInteractions(reservationService);
    }

    @Test
    @DisplayName("시술 ID 가 없으면 400 을 반환한다")
    void createReservation_missingTreatmentId_returnsBadRequest() throws Exception {
        mockMvc.perform(post(URL)
                        .header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\": \"2026-11-01T20:00:00+09:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(reservationService);
    }

    @Test
    @DisplayName("이미 예약된 시간이면 409 와 VACANCY_006 을 반환한다")
    void createReservation_timeTaken_returnsConflict() throws Exception {
        given(reservationService.createReservation(any(), any()))
                .willThrow(new BusinessException(ErrorCode.VACANCY_TIME_TAKEN));

        mockMvc.perform(post(URL)
                        .header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VACANCY_006"));
    }
}
