package com.ucamp.app.domain.auth.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.auth.service.AuthService;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("사장님 테스트 사용자는 ID·역할·매장 ID 를 반환한다")
    void getMe_owner_returnsUserWithShop() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(11))
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andExpect(jsonPath("$.data.shopId").value(1));
    }

    @Test
    @DisplayName("고객 테스트 사용자는 매장 ID 가 null 이다")
    void getMe_customer_returnsNullShop() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.shopId").doesNotExist());
    }

    @Test
    @DisplayName("비로그인이면 401 을 반환한다")
    void getMe_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("아이디 형식이 틀리면 서비스를 호출하지 않고 400 을 반환한다")
    void signupCustomer_invalidLoginId_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"AB","password":"password1!","name":"김언니","phone":"01012345678"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(authService);
    }
}
