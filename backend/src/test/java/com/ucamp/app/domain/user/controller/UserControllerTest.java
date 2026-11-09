package com.ucamp.app.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.common.auth.StubAuthFilter;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.user.dto.response.UserProfileResponse;
import com.ucamp.app.domain.user.service.UserService;
import com.ucamp.app.domain.user.service.UserWithdrawalService;

@WebMvcTest(controllers = UserController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserWithdrawalService userWithdrawalService;

    @Test
    @DisplayName("로그인 고객의 회원 정보를 반환한다")
    void getMyProfile_customer_returnsProfile() throws Exception {
        given(userService.getMyProfile(any())).willReturn(new UserProfileResponse(
                1L, "고객일", "010-1111-1111", OffsetDateTime.parse("2026-10-01T09:00:00+09:00")));

        mockMvc.perform(get("/api/v1/users/me").header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("고객일"))
                .andExpect(jsonPath("$.data.phone").value("010-1111-1111"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-01T09:00:00+09:00"));
    }

    @Test
    @DisplayName("비로그인이면 401 을 반환한다")
    void getMyProfile_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }
}
