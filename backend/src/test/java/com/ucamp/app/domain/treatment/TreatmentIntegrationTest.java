package com.ucamp.app.domain.treatment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucamp.app.common.auth.StubAuthFilter;
import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.GlobalExceptionHandler;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.config.WebConfig;
import com.ucamp.app.domain.shop.entity.Shop;
import com.ucamp.app.domain.shop.repository.ShopRepository;
import com.ucamp.app.domain.treatment.controller.TreatmentController;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;
import com.ucamp.app.domain.treatment.service.TreatmentService;

@SpringBootTest(classes = TreatmentIntegrationTest.TreatmentTestConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "treatment-test"})
@Transactional
class TreatmentIntegrationTest {

    /** 알림 도메인의 기존 스키마 불일치와 독립적으로 실제 시술 HTTP→JPA 경로를 검증한다. */
    @Configuration(proxyBeanMethods = false)
    @Profile("treatment-test")
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Shop.class, Treatment.class})
    @EnableJpaRepositories(basePackageClasses = {ShopRepository.class, TreatmentRepository.class})
    @Import({TreatmentController.class, TreatmentService.class, GlobalExceptionHandler.class,
            SecurityConfig.class, TimeConfig.class, WebConfig.class})
    static class TreatmentTestConfiguration {
    }

    private static final String URL = "/api/v1/shops/1/treatments";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TreatmentService treatmentService;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (9001, '네일'), (9002, '헤어')");
        for (long shopId : new long[]{1L, 2L}) {
            // 테스트 전용 더미 계정이며 실제 인증 정보가 아니다.
            jdbc.update("""
                    INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE,
                        ADDRESS, LATITUDE, LONGITUDE, PHONE, CREATED_AT, UPDATED_AT)
                    VALUES (?, 9001, ?, 'CHANGE_ME', '테스트 매장', 'SEOUL', '서울', 37.5, 127.0,
                        '010-0000-0000', TIMESTAMP '2026-10-23 12:00:00', TIMESTAMP '2026-10-23 12:00:00')
                    """, shopId, "CHANGE_ME_" + shopId);
        }
    }

    @Test
    @DisplayName("시술별 카테고리와 30분 배수 소요 시간을 저장하고 새로 조회한다")
    void create_validMenu_persistsIndependentCategory() throws Exception {
        long id = create(60, 50000, 9002).get("treatmentId").asLong();

        mvc.perform(get(URL + "/" + id).header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("테스트 시술"))
                .andExpect(jsonPath("$.data.categoryId").value(9002))
                .andExpect(jsonPath("$.data.categoryName").value("헤어"))
                .andExpect(jsonPath("$.data.durationMinutes").value(60))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
        assertThat(jdbc.queryForObject("SELECT PRICE FROM TREATMENT WHERE TREATMENT_ID = ?", Integer.class, id))
                .isEqualTo(50000);
        assertThat(jdbc.queryForObject("SELECT LAST_MINUTE_PRICE FROM TREATMENT WHERE TREATMENT_ID = ?", Integer.class, id))
                .isEqualTo(50000);
        mvc.perform(get(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/shops/2/treatments").header(StubAuthFilter.HEADER_NAME, "OWNER_2"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @ParameterizedTest
    @CsvSource({"0,50000", "15,50000", "45,50000", "10020,50000", "60,0", "60,-100", "60,50001"})
    @DisplayName("30분 배수가 아닌 시간·범위 초과·잘못된 정상가는 거절한다")
    void create_invalidNumbers_returnsBadRequest(int duration, int price) throws Exception {
        mvc.perform(post(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(duration, price, 9002)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TREATMENT", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 90, 9990})
    @DisplayName("30분 배수의 최소값·일반값·최대값을 허용한다")
    void create_validDurations_returnsCreated(int duration) throws Exception {
        assertThat(create(duration, 100, 9002).get("durationMinutes").asInt()).isEqualTo(duration);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리·빈 시술명·누락한 필수값을 거절한다")
    void create_invalidFields_returnsBadRequest() throws Exception {
        for (String payload : new String[]{body(60, 50000, 9999), "{}",
                body(60, 50000, 9002).replace("테스트 시술", "  ")}) {
            mvc.perform(post(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("비로그인·고객·다른 매장은 메뉴 관리에 접근할 수 없다")
    void access_nonOwner_returnsUnauthorizedOrForbidden() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (String actor : new String[]{"CUSTOMER_1", "OWNER_2", "ADMIN"}) {
            mvc.perform(get(URL).header(StubAuthFilter.HEADER_NAME, actor))
                    .andExpect(status().isForbidden());
            mvc.perform(post(URL).header(StubAuthFilter.HEADER_NAME, actor)
                            .contentType(MediaType.APPLICATION_JSON).content(body(60, 50000, 9002)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("다른 매장의 시술 ID로 조회·수정·상태 변경을 할 수 없다")
    void access_foreignTreatment_returnsNotFound() throws Exception {
        long id = create(60, 50000, 9002).get("treatmentId").asLong();
        String foreignUrl = "/api/v1/shops/2/treatments/" + id;
        mvc.perform(get(foreignUrl).header(StubAuthFilter.HEADER_NAME, "OWNER_2"))
                .andExpect(status().isNotFound());
        mvc.perform(put(foreignUrl).header(StubAuthFilter.HEADER_NAME, "OWNER_2")
                        .contentType(MediaType.APPLICATION_JSON).content(body(90, 60000, 9001)))
                .andExpect(status().isNotFound());
        mvc.perform(patch(foreignUrl + "/status").header(StubAuthFilter.HEADER_NAME, "OWNER_2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("기본 정보 수정·비활성화·재활성화가 저장되고 목록에 반영된다")
    void update_menuAndStatus_persistsChanges() throws Exception {
        long id = create(60, 50000, 9002).get("treatmentId").asLong();
        mvc.perform(put(URL + "/" + id).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(90, 60000, 9001)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationMinutes").value(90))
                .andExpect(jsonPath("$.data.regularPrice").value(60000))
                .andExpect(jsonPath("$.data.categoryName").value("네일"));
        assertThat(jdbc.queryForObject("SELECT PRICE FROM TREATMENT WHERE TREATMENT_ID = ?", Integer.class, id))
                .isEqualTo(60000);
        for (String value : new String[]{"INACTIVE", "ACTIVE"}) {
            mvc.perform(patch(URL + "/" + id + "/status").header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + value + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(value));
            mvc.perform(get(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                    .andExpect(jsonPath("$.data[0].status").value(value));
        }
    }

    @Test
    @DisplayName("카테고리 목록은 서버의 실제 목록을 반환한다")
    void getCategories_returnsAvailableCategories() throws Exception {
        mvc.perform(get(URL + "/categories").header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].categoryId").value(9001));
    }

    @Test
    @DisplayName("비활성화·수정 시 빈자리와 예약을 보존하고 잘못된 정상가를 거절한다")
    void update_discountedTreatment_preservesPricesAndReservation() throws Exception {
        long id = create(60, 50000, 9002).get("treatmentId").asLong();
        jdbc.update("""
                UPDATE TREATMENT SET PRICE = 40000, LAST_MINUTE_PRICE = 30000,
                    TREATMENT_TYPE = 'MODEL', MODEL_CONDITION = '모델 조건' WHERE TREATMENT_ID = ?
                """, id);
        jdbc.update("""
                INSERT INTO APP_USER (USER_ID, LOGIN_ID, PASSWORD_HASH, NAME, PHONE, CREATED_AT, UPDATED_AT)
                VALUES (9001, 'CHANGE_ME', 'CHANGE_ME', '고객', '010-0000-0000',
                    TIMESTAMP '2026-10-23 12:00:00', TIMESTAMP '2026-10-23 12:00:00')
                """);
        jdbc.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                    APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (9001, 9001, ?, 'CONFIRMED', TIMESTAMP '2026-10-25 12:00:00',
                    TIMESTAMP '2026-10-25 13:00:00', 40000, 20000, TIMESTAMP '2026-10-23 12:05:00',
                    TIMESTAMP '2026-10-23 12:00:00', TIMESTAMP '2026-10-23 12:00:00')
                """, id);
        entityManager.clear();

        jdbc.update("""
                INSERT INTO SHOP_SCHEDULE_EXCEPTION (EXCEPTION_ID, SHOP_ID, EXCEPTION_TYPE,
                    STARTS_AT, ENDS_AT, CREATED_AT, UPDATED_AT)
                VALUES (9001, 1, 'OPEN', TIMESTAMP '2026-10-25 12:00:00',
                    TIMESTAMP '2026-10-25 14:00:00', TIMESTAMP '2026-10-23 12:00:00',
                    TIMESTAMP '2026-10-23 12:00:00')
                """);
        var reservationBefore = jdbc.queryForMap("SELECT * FROM RESERVATION WHERE RESERVATION_ID = 9001");
        var slotBefore = jdbc.queryForMap("SELECT * FROM SHOP_SCHEDULE_EXCEPTION WHERE EXCEPTION_ID = 9001");
        mvc.perform(patch(URL + "/" + id + "/status").header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        entityManager.clear();
        assertThat(jdbc.queryForMap("SELECT * FROM RESERVATION WHERE RESERVATION_ID = 9001"))
                .isEqualTo(reservationBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM SHOP_SCHEDULE_EXCEPTION WHERE EXCEPTION_ID = 9001"))
                .isEqualTo(slotBefore);

        mvc.perform(put(URL + "/" + id).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(90, 60000, 9001)))
                .andExpect(status().isOk());
        var treatment = jdbc.queryForMap("SELECT * FROM TREATMENT WHERE TREATMENT_ID = ?", id);
        assertThat(((Number) treatment.get("PRICE")).intValue()).isEqualTo(40000);
        assertThat(((Number) treatment.get("LAST_MINUTE_PRICE")).intValue()).isEqualTo(30000);
        assertThat(treatment.get("TREATMENT_TYPE")).isEqualTo("MODEL");
        assertThat(treatment.get("MODEL_CONDITION")).isEqualTo("모델 조건");
        var reservation = jdbc.queryForMap("SELECT * FROM RESERVATION WHERE RESERVATION_ID = 9001");
        assertThat(((Number) reservation.get("APPLIED_PRICE")).intValue()).isEqualTo(40000);
        assertThat(((Number) reservation.get("DEPOSIT")).intValue()).isEqualTo(20000);
        assertThat(reservation.get("ENDS_AT").toString()).startsWith("2026-10-25 13:00:00");

        mvc.perform(put(URL + "/" + id).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(90, 35000, 9001)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비활성화는 신규 선택에서 제외하고 전체 조회를 유지하며 재활성화하면 다시 선택된다")
    void deactivate_excludesSelectionAndAllowsReactivation() throws Exception {
        long inactiveId = create(60, 50000, 9002).get("treatmentId").asLong();
        long activeId = create(30, 30000, 9001).get("treatmentId").asLong();
        AuthUser owner = new AuthUser(1L, UserRole.OWNER, 1L);
        for (String value : new String[]{"INACTIVE", "INACTIVE"}) {
            mvc.perform(patch(URL + "/" + inactiveId + "/status")
                            .header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + value + "\"}"))
                    .andExpect(status().isOk());
        }
        entityManager.clear();
        mvc.perform(get(URL + "/selectable").header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].treatmentId").value(activeId));
        mvc.perform(get(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get(URL + "/" + inactiveId).header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("INACTIVE"));
        assertThatThrownBy(() -> treatmentService.requireSelectableTreatment(owner, 1L, inactiveId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("비활성 시술");
        assertThat(treatmentService.requireSelectableTreatment(owner, 1L, activeId).treatmentId())
                .isEqualTo(activeId);
        mvc.perform(patch(URL + "/" + inactiveId + "/status")
                        .header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(URL + "/selectable").header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("신규 선택 목록은 비로그인과 다른 매장 접근을 거절하고 빈 목록을 허용한다")
    void selectable_checksOwnerAndReturnsEmptyList() throws Exception {
        mvc.perform(get(URL + "/selectable")).andExpect(status().isUnauthorized());
        for (String actor : new String[]{"OWNER_2", "CUSTOMER_1", "ADMIN"}) {
            mvc.perform(get(URL + "/selectable").header(StubAuthFilter.HEADER_NAME, actor))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(URL + "/selectable").header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    private JsonNode create(int duration, int price, long category) throws Exception {
        String response = mvc.perform(post(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(duration, price, category)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("data");
    }

    private String body(int duration, int price, long category) {
        return """
                {"name":"테스트 시술","description":"시술 설명","regularPrice":%d,
                 "categoryId":%d,"durationMinutes":%d}
                """.formatted(price, category, duration);
    }
}
