package com.ucamp.app.domain.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
import com.ucamp.app.domain.schedule.dto.request.ScheduleExceptionSaveRequest;
import com.ucamp.app.domain.schedule.dto.response.ScheduleExceptionResponse;
import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;
import com.ucamp.app.domain.schedule.service.ShopScheduleExceptionService;

@WebMvcTest(controllers = ShopScheduleExceptionController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ShopScheduleExceptionControllerTest {

    private static final String URL = "/api/v1/schedule-exceptions";
    private static final String VALID_BODY = """
            {"exceptionType":"CLOSE","startsAt":"2026-10-25T12:00:00+09:00",
             "endsAt":"2026-10-25T13:00:00+09:00","reason":"점심시간"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShopScheduleExceptionService shopScheduleExceptionService;

    @Test
    @DisplayName("매장이 등록하면 201 과 +09:00 시각을 반환한다")
    void createException_owner_returnsCreated() throws Exception {
        OffsetDateTime noon = OffsetDateTime.of(2026, 10, 25, 12, 0, 0, 0, ZoneOffset.ofHours(9));
        given(shopScheduleExceptionService.createException(any(AuthUser.class), any(ScheduleExceptionSaveRequest.class)))
                .willReturn(new ScheduleExceptionResponse(1L, ScheduleExceptionType.CLOSE, noon, noon.plusHours(1),
                        "점심시간", null));

        mockMvc.perform(post(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.exceptionId").value(1))
                .andExpect(jsonPath("$.data.startsAt").value("2026-10-25T12:00:00+09:00"));
    }

    @Test
    @DisplayName("예약 빼고 닫기 값을 서비스에 넘기고, 없으면 null 로 넘긴다")
    void createException_excludeReserved_passedToService() throws Exception {
        String withFlag = VALID_BODY.replace("{\"exceptionType\"", "{\"excludeReserved\":true,\"exceptionType\"");
        OffsetDateTime noon = OffsetDateTime.of(2026, 10, 25, 12, 0, 0, 0, ZoneOffset.ofHours(9));
        given(shopScheduleExceptionService.createException(any(AuthUser.class), any(ScheduleExceptionSaveRequest.class)))
                .willReturn(new ScheduleExceptionResponse(1L, ScheduleExceptionType.CLOSE, noon, noon.plusHours(1),
                        "점심시간", null));

        mockMvc.perform(post(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(withFlag))
                .andExpect(status().isCreated());
        mockMvc.perform(post(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<ScheduleExceptionSaveRequest> request =
                ArgumentCaptor.forClass(ScheduleExceptionSaveRequest.class);
        verify(shopScheduleExceptionService, times(2)).createException(any(AuthUser.class), request.capture());
        assertThat(request.getAllValues()).extracting(ScheduleExceptionSaveRequest::excludeReserved)
                .containsExactly(true, null);
    }

    @Test
    @DisplayName("비로그인이면 401 을 반환하고 서비스를 호출하지 않는다")
    void createException_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(shopScheduleExceptionService);
    }

    @Test
    @DisplayName("유형이 없으면 400 을 반환한다")
    void createException_missingType_returnsBadRequest() throws Exception {
        String body = """
                {"startsAt":"2026-10-25T12:00:00+09:00","endsAt":"2026-10-25T13:00:00+09:00"}
                """;

        mockMvc.perform(post(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("삭제하면 본문 없이 204 를 반환한다")
    void deleteException_returnsNoContent() throws Exception {
        mockMvc.perform(delete(URL + "/1").header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isNoContent());
        verify(shopScheduleExceptionService).deleteException(any(AuthUser.class), eq(1L));
    }
}
