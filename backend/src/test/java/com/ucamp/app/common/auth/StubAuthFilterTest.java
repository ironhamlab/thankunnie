package com.ucamp.app.common.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.config.SecurityConfig;

@WebMvcTest(controllers = AuthTestController.class)
@Import({SecurityConfig.class, AuthTestController.class})
@ActiveProfiles("test")
class StubAuthFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("X-Test-User 헤더의 테스트 사용자가 로그인 사용자로 주입된다")
    void loginUser_validHeader_injectsTestUser() throws Exception {
        mockMvc.perform(get("/test/auth/me").header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(11))
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andExpect(jsonPath("$.data.shopId").value(1));
    }

    @Test
    @DisplayName("Authorization Bearer 값의 테스트 사용자도 로그인 사용자로 주입된다")
    void loginUser_bearerToken_injectsTestUser() throws Exception {
        mockMvc.perform(get("/test/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer CUSTOMER_2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("X-Test-User 와 Bearer 가 함께 오면 X-Test-User 가 우선한다")
    void loginUser_bothHeaders_prefersTestUserHeader() throws Exception {
        mockMvc.perform(get("/test/auth/me")
                        .header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer CUSTOMER_2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(11));
    }

    @Test
    @DisplayName("Bearer 가 아니거나 알 수 없는 값이면 비로그인으로 처리한다")
    void loginUser_invalidAuthorization_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/auth/me").header(HttpHeaders.AUTHORIZATION, "Basic OWNER_1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/test/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOi.fake.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("헤더가 없으면 로그인 필수 API 는 401 을 반환한다")
    void loginUser_noHeader_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("알 수 없는 헤더 값은 비로그인으로 처리한다")
    void loginUser_unknownHeader_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/auth/me").header(StubAuthFilter.HEADER_NAME, "HACKER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인 선택 API 는 비로그인이면 null 을 주입한다")
    void optionalLoginUser_noHeader_injectsNull() throws Exception {
        mockMvc.perform(get("/test/auth/optional"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("anonymous"));
    }

    @Test
    @DisplayName("다른 매장 계정은 403 을 반환한다")
    void requireShopOwner_otherOwner_returnsForbidden() throws Exception {
        mockMvc.perform(get("/test/auth/shops/1").header(StubAuthFilter.HEADER_NAME, "OWNER_2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_403"));
    }

    @Test
    @DisplayName("본인 매장 계정은 200 을 반환한다")
    void requireShopOwner_ownOwner_returnsOk() throws Exception {
        mockMvc.perform(get("/test/auth/shops/1").header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk());
    }
}
