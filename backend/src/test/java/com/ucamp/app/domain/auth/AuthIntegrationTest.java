package com.ucamp.app.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucamp.app.common.exception.GlobalExceptionHandler;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.config.WebConfig;
import com.ucamp.app.domain.auth.controller.AuthController;
import com.ucamp.app.domain.auth.entity.ShopAccount;
import com.ucamp.app.domain.auth.repository.ShopAccountRepository;
import com.ucamp.app.domain.auth.service.AuthService;
import com.ucamp.app.domain.shop.controller.ShopController;
import com.ucamp.app.domain.shop.entity.Shop;
import com.ucamp.app.domain.shop.repository.ShopRepository;
import com.ucamp.app.domain.shop.service.ShopService;
import com.ucamp.app.domain.user.entity.AppUser;
import com.ucamp.app.domain.user.repository.AppUserRepository;

@SpringBootTest(classes = AuthIntegrationTest.AuthTestConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "auth-test"})
@Transactional
class AuthIntegrationTest {

    /** 가입·로그인 → JWT → 기존 @LoginUser·매장 권한 검사까지 실제 HTTP→JPA 경로로 확인한다. */
    @Configuration(proxyBeanMethods = false)
    @Profile("auth-test")
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {AppUser.class, ShopAccount.class, Shop.class})
    @EnableJpaRepositories(basePackageClasses = {AppUserRepository.class, ShopAccountRepository.class,
            ShopRepository.class})
    @Import({AuthController.class, AuthService.class, ShopController.class, ShopService.class,
            GlobalExceptionHandler.class, SecurityConfig.class, TimeConfig.class, WebConfig.class})
    static class AuthTestConfiguration {
    }

    private static final long CATEGORY_ID = 9001L;
    // 테스트 전용 더미 비밀번호
    private static final String PASSWORD = "CHANGE_ME-1234";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (?, '네일')", CATEGORY_ID);
    }

    @Test
    @DisplayName("고객 가입 후 받은 토큰으로 현재 사용자를 조회한다")
    void signupCustomer_thenMe_returnsCustomer() throws Exception {
        JsonNode data = data(signupCustomer("unnie01").andExpect(status().isCreated()));

        assertThat(data.get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(data.get("shopId").isNull()).isTrue();
        long userId = data.get("userId").asLong();

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(data)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
        assertThat(jdbc.queryForObject("SELECT PHONE FROM APP_USER WHERE USER_ID = ?", String.class, userId))
                .isEqualTo("010-1234-5678");
        assertThat(jdbc.queryForObject("SELECT ROLE FROM APP_USER WHERE USER_ID = ?", String.class, userId))
                .isEqualTo("USER");
    }

    @Test
    @DisplayName("같은 아이디로 다시 가입하면 409 AUTH_001")
    void signupCustomer_duplicateLoginId_returnsConflict() throws Exception {
        signupCustomer("unnie02").andExpect(status().isCreated());

        signupCustomer("unnie02")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    @DisplayName("가입한 고객은 로그인할 수 있고, 비밀번호가 틀리면 401 AUTH_002")
    void loginCustomer_afterSignup() throws Exception {
        signupCustomer("unnie03").andExpect(status().isCreated());

        login("/api/v1/auth/login", "unnie03", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
        login("/api/v1/auth/login", "unnie03", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
        login("/api/v1/auth/login", "nobody", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("비활성 회원은 비밀번호가 맞아도 403 AUTH_003")
    void loginCustomer_inactive_returnsForbidden() throws Exception {
        signupCustomer("unnie04").andExpect(status().isCreated());
        jdbc.update("UPDATE APP_USER SET STATUS = 'INACTIVE' WHERE LOGIN_ID = 'unnie04'");
        // 가입 때 영속성 컨텍스트에 남은 ACTIVE 엔티티 대신 DB 값을 읽게 한다
        entityManager.clear();

        login("/api/v1/auth/login", "unnie04", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("매장 STATUS 는 노출 여부라 INACTIVE 매장도 로그인한다")
    void loginShop_inactiveShop_stillLogsIn() throws Exception {
        signupShop("closedshop").andExpect(status().isCreated());
        jdbc.update("UPDATE SHOP SET STATUS = 'INACTIVE' WHERE LOGIN_ID = 'closedshop'");
        entityManager.clear();

        login("/api/v1/auth/shops/login", "closedshop", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("OWNER"));
    }

    @Test
    @DisplayName("매장 가입 토큰으로 본인 매장은 조회되고 다른 매장은 403")
    void signupShop_thenOwnShopOnly() throws Exception {
        JsonNode data = data(signupShop("nailshop").andExpect(status().isCreated()));

        assertThat(data.get("role").asText()).isEqualTo("OWNER");
        long shopId = data.get("shopId").asLong();
        assertThat(data.get("userId").asLong()).isEqualTo(shopId);

        mvc.perform(get("/api/v1/shops/{shopId}", shopId).header(HttpHeaders.AUTHORIZATION, bearer(data)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("언니네 네일"))
                .andExpect(jsonPath("$.data.regionCode").value("11680"));
        mvc.perform(get("/api/v1/shops/{shopId}", shopId + 1).header(HttpHeaders.AUTHORIZATION, bearer(data)))
                .andExpect(status().isForbidden());

        login("/api/v1/auth/shops/login", "nailshop", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopId").value(shopId));
        assertThat(jdbc.queryForObject("SELECT STATUS FROM SHOP WHERE SHOP_ID = ?", String.class, shopId))
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("없는 카테고리로 매장 가입하면 400")
    void signupShop_unknownCategory_returnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/shops/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shopSignupBody("nailshop2", 99999L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("변조·만료 등 유효하지 않은 토큰은 비로그인으로 보고 401")
    void me_invalidToken_returnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("매장 가입용 카테고리를 로그인 없이 조회한다")
    void getShopCategories_anonymous_returnsList() throws Exception {
        mvc.perform(get("/api/v1/auth/shop-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].categoryId").value(CATEGORY_ID))
                .andExpect(jsonPath("$.data[0].name").value("네일"));
    }

    private ResultActions signupCustomer(String loginId) throws Exception {
        return mvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"loginId":"%s","password":"%s","name":"김언니","phone":"01012345678"}
                        """.formatted(loginId, PASSWORD)));
    }

    private ResultActions signupShop(String loginId) throws Exception {
        return mvc.perform(post("/api/v1/auth/shops/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(shopSignupBody(loginId, CATEGORY_ID)));
    }

    private static String shopSignupBody(String loginId, long categoryId) {
        return """
                {"loginId":"%s","password":"%s","categoryId":%d,"name":"언니네 네일","regionCode":"11680",
                 "address":"서울특별시 강남구 테헤란로 1","addressDetail":"2층",
                 "latitude":37.4979,"longitude":127.0276,"phone":"02-111-1111"}
                """.formatted(loginId, PASSWORD, categoryId);
    }

    private ResultActions login(String url, String loginId, String password) throws Exception {
        return mvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"loginId":"%s","password":"%s"}
                        """.formatted(loginId, password)));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private static String bearer(JsonNode data) {
        return "Bearer " + data.get("accessToken").asText();
    }
}
