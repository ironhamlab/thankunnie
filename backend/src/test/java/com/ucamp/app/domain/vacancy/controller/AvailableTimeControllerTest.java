package com.ucamp.app.domain.vacancy.controller;

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

import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.vacancy.dto.response.AvailableTimesResponse;
import com.ucamp.app.domain.vacancy.service.AvailableTimeQueryService;

@WebMvcTest(controllers = AvailableTimeController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AvailableTimeControllerTest {

    private static final String URL = "/api/v1/available-times";
    private static final LocalDate DATE = LocalDate.of(2026, 11, 2);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvailableTimeQueryService availableTimeQueryService;

    @Test
    @DisplayName("비로그인으로 조회하면 시작 가능 시각을 +09:00 형식으로 반환한다")
    void getAvailableTimes_anonymous_returnsTimes() throws Exception {
        OffsetDateTime ten = OffsetDateTime.of(2026, 11, 2, 10, 0, 0, 0, ZoneOffset.ofHours(9));
        given(availableTimeQueryService.getAvailableTimes(1L, DATE, 60))
                .willReturn(new AvailableTimesResponse(1L, DATE, 60, List.of(ten)));

        mockMvc.perform(get(URL).param("shopId", "1").param("date", "2026-11-02").param("durationMinutes", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.startTimes[0]").value("2026-11-02T10:00:00+09:00"));
    }

    @Test
    @DisplayName("소요 시간을 생략하면 30분으로 조회한다")
    void getAvailableTimes_noDuration_usesThirtyMinutes() throws Exception {
        given(availableTimeQueryService.getAvailableTimes(eq(1L), eq(DATE), eq(30)))
                .willReturn(new AvailableTimesResponse(1L, DATE, 30, List.of()));

        mockMvc.perform(get(URL).param("shopId", "1").param("date", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationMinutes").value(30));
    }

    @Test
    @DisplayName("매장이나 날짜가 없거나 날짜 형식이 틀리면 400 을 반환한다")
    void getAvailableTimes_missingOrInvalidParams_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL).param("date", "2026-11-02")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("shopId", "1").param("date", "11/02"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(availableTimeQueryService);
    }
}
