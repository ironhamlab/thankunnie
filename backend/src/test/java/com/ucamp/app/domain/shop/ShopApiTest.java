package com.ucamp.app.domain.shop;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.domain.shop.repository.ShopRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopApiTest {

    private static final String REQUEST = """
            {"name":"매장", "description":"소개", "address":"서울시", "addressDetail":"1층",
             "latitude":37.5665,"longitude":126.9780}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShopRepository shopRepository;

    @BeforeEach
    void cleanUp() {
        shopRepository.deleteAll();
    }

    @Test
    @DisplayName("본인 매장을 등록하고 수정한 정보가 다시 조회된다")
    void saveShop_owner_persistsInformation() throws Exception {
        mockMvc.perform(post("/api/v1/shops").header("X-Test-User", "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shopId").value(1));
        mockMvc.perform(put("/api/v1/shops/1").header("X-Test-User", "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST.replace("매장", "새이름")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shops/1").header("X-Test-User", "OWNER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.latitude").value(37.5665));
    }

    @Test
    @DisplayName("다른 매장 사장님과 고객은 수정할 수 없다")
    void updateShop_otherUser_returnsForbidden() throws Exception {
        for (String user : new String[] {"OWNER_2", "CUSTOMER_1"}) {
            mockMvc.perform(put("/api/v1/shops/1").header("X-Test-User", user)
                            .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("비로그인 사용자의 등록은 거부된다")
    void createShop_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/shops").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("범위 밖 좌표와 공백 매장명은 저장되지 않는다")
    void createShop_invalidInput_returnsBadRequest() throws Exception {
        for (String request : new String[] {REQUEST.replace("37.5665", "90.1"),
                REQUEST.replace("126.9780", "180.1"), REQUEST.replace("매장", " ")}) {
            mockMvc.perform(post("/api/v1/shops").header("X-Test-User", "OWNER_1")
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("미등록 매장은 404를 반환한다")
    void getShop_missing_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/shops/1").header("X-Test-User", "OWNER_1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("중복 등록은 기존 정보를 덮어쓰지 않는다")
    void createShop_duplicate_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/shops").header("X-Test-User", "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/shops").header("X-Test-User", "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest());
    }
}
