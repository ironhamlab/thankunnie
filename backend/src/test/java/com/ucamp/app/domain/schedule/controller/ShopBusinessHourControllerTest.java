package com.ucamp.app.domain.schedule.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    @DisplayName("비로그인 조회는 401 을 반환한다")
    void getBusinessHours_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(shopBusinessHourService);
    }
}
