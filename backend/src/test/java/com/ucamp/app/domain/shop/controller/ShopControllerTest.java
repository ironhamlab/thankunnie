package com.ucamp.app.domain.shop.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.shop.service.ShopService;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShopController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ShopControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ShopService service;

    private static final String BODY = """
            {"categoryId":1,"name":"매장","regionCode":"11680","address":"서울",
             "latitude":37.5,"longitude":127.0,"phone":"02-000-0000"}
            """;

    @Test
    @DisplayName("비로그인 조회와 수정은 인증을 요구한다")
    void access_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/shops/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/shops/1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("위도·경도 범위 초과와 정밀도 초과를 거부한다")
    void update_invalidCoordinates_returnsBadRequest() throws Exception {
        for (String body : new String[] {BODY.replace("37.5", "90.1"),
                BODY.replace("127.0", "180.1"), BODY.replace("37.5", "37.12345678")}) {
            mockMvc.perform(put("/api/v1/shops/1").header("X-Test-User", "OWNER_1")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("필수값 누락과 공백 매장명을 거부한다")
    void update_missingFields_returnsBadRequest() throws Exception {
        for (String body : new String[] {"{}", BODY.replace("매장", " ")}) {
            mockMvc.perform(put("/api/v1/shops/1").header("X-Test-User", "OWNER_1")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
