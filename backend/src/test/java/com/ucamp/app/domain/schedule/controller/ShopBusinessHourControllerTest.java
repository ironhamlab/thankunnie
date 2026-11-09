package com.ucamp.app.domain.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.StubAuthFilter;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.schedule.dto.request.BusinessHourSaveRequest;
import com.ucamp.app.domain.schedule.dto.response.BusinessHourClosingImpactResponse;
import com.ucamp.app.domain.schedule.dto.response.BusinessHourResponse;
import com.ucamp.app.domain.schedule.entity.BusinessDay;
import com.ucamp.app.domain.schedule.service.ShopBusinessHourService;

@WebMvcTest(controllers = ShopBusinessHourController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ShopBusinessHourControllerTest {

    private static final String URL = "/api/v1/business-hours";
    private static final String WEEK_BODY = """
            {"days":[
             {"dayOfWeek":"MON","closed":false,"openTime":"10:00","closeTime":"20:00"},
             {"dayOfWeek":"TUE","closed":false,"openTime":"10:00","closeTime":"20:00"},
             {"dayOfWeek":"WED","closed":false,"openTime":"10:00","closeTime":"20:00"},
             {"dayOfWeek":"THU","closed":false,"openTime":"10:00","closeTime":"20:00"},
             {"dayOfWeek":"FRI","closed":false,"openTime":"10:00","closeTime":"20:00"},
             {"dayOfWeek":"SAT","closed":false,"openTime":"10:00","closeTime":"18:00"},
             {"dayOfWeek":"SUN","closed":true,"openTime":null,"closeTime":null}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShopBusinessHourService shopBusinessHourService;

    @Test
    @DisplayName("저장하면 HH:mm 형식 시각으로 7개 요일을 반환한다")
    void updateBusinessHours_owner_returnsWeek() throws Exception {
        given(shopBusinessHourService.updateBusinessHours(any(AuthUser.class), any(BusinessHourSaveRequest.class)))
                .willReturn(List.of(new BusinessHourResponse(BusinessDay.MON, false, LocalTime.of(10, 0),
                        LocalTime.of(20, 30))));

        mockMvc.perform(put(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(WEEK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].openTime").value("10:00"))
                .andExpect(jsonPath("$.data[0].closeTime").value("20:30"));
    }

    @Test
    @DisplayName("요일이 7개가 아니면 400 을 반환한다")
    void updateBusinessHours_notSevenDays_returnsBadRequest() throws Exception {
        String body = """
                {"days":[{"dayOfWeek":"MON","closed":true}]}
                """;

        mockMvc.perform(put(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(shopBusinessHourService);
    }

    @Test
    @DisplayName("함께 닫기 값을 서비스에 넘기고, 없으면 null 로 넘긴다")
    void updateBusinessHours_closeOpenExceptions_passedToService() throws Exception {
        String withFlag = WEEK_BODY.replace("{\"days\":", "{\"closeOpenExceptions\":true,\"days\":");

        mockMvc.perform(put(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(withFlag))
                .andExpect(status().isOk());
        mockMvc.perform(put(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(WEEK_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<BusinessHourSaveRequest> request = ArgumentCaptor.forClass(BusinessHourSaveRequest.class);
        verify(shopBusinessHourService, times(2)).updateBusinessHours(any(AuthUser.class), request.capture());
        assertThat(request.getAllValues()).extracting(BusinessHourSaveRequest::closeOpenExceptions)
                .containsExactly(true, null);
    }

    @Test
    @DisplayName("휴무 전환 영향은 여러 요일을 받아 예약과 열기를 반환한다")
    void getClosingImpact_owner_returnsImpact() throws Exception {
        OffsetDateTime start = OffsetDateTime.of(2026, 11, 26, 14, 0, 0, 0, ZoneOffset.ofHours(9));
        given(shopBusinessHourService.getClosingImpact(any(AuthUser.class),
                eq(List.of(BusinessDay.THU, BusinessDay.FRI))))
                .willReturn(new BusinessHourClosingImpactResponse(
                        List.of(new BusinessHourClosingImpactResponse.ReservationTime(start, start.plusHours(1))),
                        List.of(new BusinessHourClosingImpactResponse.OpenException(3L, start.plusHours(7),
                                start.plusHours(9), true))));

        mockMvc.perform(get(URL + "/closing-impact").param("days", "THU", "FRI")
                        .header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservations[0].startsAt").value("2026-11-26T14:00:00+09:00"))
                .andExpect(jsonPath("$.data.openExceptions[0].exceptionId").value(3))
                .andExpect(jsonPath("$.data.openExceptions[0].closable").value(true));
    }

    @Test
    @DisplayName("휴무 전환 영향에 요일이 없거나 잘못되면 400 을 반환한다")
    void getClosingImpact_invalidDays_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL + "/closing-impact").header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL + "/closing-impact").param("days", "HOLIDAY")
                        .header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(shopBusinessHourService);
    }

    @Test
    @DisplayName("비로그인 휴무 전환 영향 조회는 401 을 반환한다")
    void getClosingImpact_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL + "/closing-impact").param("days", "THU"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(shopBusinessHourService);
    }

    @Test
    @DisplayName("비로그인 조회는 401 을 반환한다")
    void getBusinessHours_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(shopBusinessHourService);
    }
}
