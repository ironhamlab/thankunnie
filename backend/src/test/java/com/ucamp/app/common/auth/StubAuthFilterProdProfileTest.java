package com.ucamp.app.common.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.config.SecurityConfig;

@WebMvcTest(controllers = AuthTestController.class)
@Import({SecurityConfig.class, AuthTestController.class})
@ActiveProfiles("prod")
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class StubAuthFilterProdProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("prod 프로필에서는 X-Test-User 헤더가 있어도 인증하지 않는다")
    void loginUser_prodProfile_ignoresTestHeader() throws Exception {
        mockMvc.perform(get("/test/auth/me").header(StubAuthFilter.HEADER_NAME, "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("prod 프로필에서는 Bearer 테스트 사용자 값도 인증하지 않는다")
    void loginUser_prodProfile_ignoresBearerTestUser() throws Exception {
        mockMvc.perform(get("/test/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer ADMIN"))
                .andExpect(status().isUnauthorized());
    }
}
