package com.ucamp.app.domain.shop;

import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ucamp.app.domain.shop.service.ShopImageStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopImageIntegrationTest {

    @TempDir
    static Path directory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.shop-image.directory", () -> directory.toString());
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestRestTemplate http;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (9701, '이미지 테스트')");
        LocalDateTime time = LocalDateTime.of(2026, 10, 26, 12, 0);
        // OWNER_1은 인증 공통 stub의 shopId=1을 사용한다. 계정 값은 테스트 전용 더미다.
        jdbc.update("""
                INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE,
                    ADDRESS, LATITUDE, LONGITUDE, PHONE, STATUS, CREATED_AT, UPDATED_AT)
                VALUES (1, 9701, 'CHANGE_ME', 'CHANGE_ME', '이미지 테스트', '11680',
                    '서울', 37.5, 127.0, '02-000-0000', 'ACTIVE', ?, ?)
                """, time, time);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM SHOP WHERE SHOP_ID = 1");
        jdbc.update("DELETE FROM CATEGORY WHERE CATEGORY_ID = 9701");
    }

    @Test
    @DisplayName("업로드·공개 조회·교체·삭제를 수행하고 과거 URL은 접근할 수 없다")
    void image_fullLifecycle_persistsAndServesCurrentOnly() throws Exception {
        MockMultipartFile png = ShopImageFixture.image("png");
        String originalUrl = upload(png);
        mvc.perform(get(originalUrl)).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png.getBytes()));

        MockMultipartFile jpg = ShopImageFixture.image("jpg");
        String replacedUrl = upload(jpg);
        assertThat(replacedUrl).isNotEqualTo(originalUrl);
        mvc.perform(get(originalUrl)).andExpect(status().isNotFound());
        mvc.perform(get(replacedUrl)).andExpect(status().isOk()).andExpect(content().bytes(jpg.getBytes()));

        mvc.perform(delete("/api/v1/shops/1/image").header("X-Test-User", "OWNER_1"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get(replacedUrl)).andExpect(status().isNotFound());
        assertThat(imageUrl()).isNull();
    }

    @Test
    @DisplayName("누락·위장·손상·용량 초과로 교체가 실패해도 기존 이미지가 조회된다")
    void upload_invalidFile_keepsPreviousUrlAndBytes() throws Exception {
        MockMultipartFile png = ShopImageFixture.image("png");
        String url = upload(png);
        MockMultipartFile[] invalidFiles = {
                new MockMultipartFile("file", "bad.png", "image/png", "not an image".getBytes()),
                new MockMultipartFile("file", "bad.jpg", "image/jpeg", png.getBytes()),
                new MockMultipartFile("file", "big.png", "image/png", new byte[ShopImageStorage.MAX_BYTES + 1])
        };
        for (MockMultipartFile file : invalidFiles) {
            mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/1/image").file(file)
                    .header("X-Test-User", "OWNER_1")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/1/image").header("X-Test-User", "OWNER_1"))
                .andExpect(status().isBadRequest());
        assertThat(imageUrl()).isEqualTo(url);
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(png.getBytes()));
    }

    @Test
    @DisplayName("비로그인은 401, 타 매장 사장님·고객·관리자는 403이며 이미지가 유지된다")
    void mutate_unauthorized_keepsImage() throws Exception {
        String url = upload(ShopImageFixture.image("png"));
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/1/image").file(ShopImageFixture.image("png")))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/shops/1/image")).andExpect(status().isUnauthorized());
        for (String user : new String[] {"OWNER_2", "CUSTOMER_1", "ADMIN"}) {
            mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/1/image").file(ShopImageFixture.image("png"))
                    .header("X-Test-User", user)).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/shops/1/image").header("X-Test-User", user))
                    .andExpect(status().isForbidden());
        }
        assertThat(imageUrl()).isEqualTo(url);
    }

    @Test
    @DisplayName("실제 HTTP multipart 파서의 5MB 초과 오류도 공통 400 응답이며 기존 이미지를 유지한다")
    void upload_httpMultipartLimit_returnsCommonBadRequest() throws Exception {
        String url = upload(ShopImageFixture.image("png"));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(new byte[ShopImageStorage.MAX_BYTES + 1]) {
            @Override
            public String getFilename() {
                return "large.png";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Test-User", "OWNER_1");
        var response = http.exchange("/api/v1/shops/1/image", HttpMethod.PUT,
                new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("COMMON_400");
        assertThat(imageUrl()).isEqualTo(url);
    }

    private String upload(MockMultipartFile file) throws Exception {
        String response = mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/1/image").file(file)
                .header("X-Test-User", "OWNER_1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("imageUrl").asText();
    }

    private String imageUrl() {
        return jdbc.queryForObject("SELECT IMAGE_URL FROM SHOP WHERE SHOP_ID = 1", String.class);
    }
}
