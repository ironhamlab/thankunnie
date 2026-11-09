package com.ucamp.app.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;

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

import com.ucamp.app.common.exception.GlobalExceptionHandler;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.config.WebConfig;
import com.ucamp.app.domain.auth.controller.AdminShopApplicationController;
import com.ucamp.app.domain.auth.entity.ShopAccount;
import com.ucamp.app.domain.auth.repository.ShopAccountRepository;
import com.ucamp.app.domain.auth.service.AdminShopApplicationService;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.shop.entity.ShopCategory;
import com.ucamp.app.domain.shop.repository.ShopCategoryRepository;

@SpringBootTest(classes = AdminShopApplicationIntegrationTest.TestConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "admin-shop-test"})
@Transactional
class AdminShopApplicationIntegrationTest {

    @Configuration(proxyBeanMethods = false)
    @Profile("admin-shop-test")
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {ShopAccount.class, ShopCategory.class, Reservation.class})
    @EnableJpaRepositories(basePackageClasses = {ShopAccountRepository.class, ShopCategoryRepository.class,
            ReservationRepository.class})
    @Import({AdminShopApplicationController.class, AdminShopApplicationService.class,
            GlobalExceptionHandler.class, SecurityConfig.class, TimeConfig.class, WebConfig.class})
    static class TestConfiguration {
    }

    private static final String URL = "/api/v1/admin/shop-applications";
    private static final String ADMIN = "Bearer ADMIN";
    private static final long CATEGORY_ID = 9001L;
    private static final long PENDING_ID = 9101L;
    private static final long LATER_PENDING_ID = 9102L;
    private static final long ACTIVE_ID = 9103L;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (?, '네일')", CATEGORY_ID);
        insertShop(LATER_PENDING_ID, "PENDING", "2026-11-02 10:00:00");
        insertShop(PENDING_ID, "PENDING", "2026-11-01 10:00:00");
        insertShop(ACTIVE_ID, "ACTIVE", "2026-10-01 10:00:00");
    }

    @Test
    @DisplayName("승인 대기 신청을 최신 신청 순으로 조회한다")
    void getApplications_defaultsToPendingNewestFirst() throws Exception {
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].shopId").value(LATER_PENDING_ID))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("네일"))
                .andExpect(jsonPath("$.data.content[0].appliedAt").value("2026-11-02T10:00:00+09:00"))
                .andExpect(jsonPath("$.data.content[1].shopId").value(PENDING_ID));
    }

    @Test
    @DisplayName("사업자등록번호를 함께 내려주고, 번호가 없는 기존 매장은 null 이다")
    void getApplications_returnsBusinessNumber() throws Exception {
        jdbc.update("UPDATE SHOP SET BUSINESS_NUMBER = '1234567891' WHERE SHOP_ID = ?", PENDING_ID);

        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].businessNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.content[1].businessNumber").value("1234567891"));
    }

    @Test
    @DisplayName("상태로 신청 목록을 거른다")
    void getApplications_filtersByStatus() throws Exception {
        mvc.perform(get(URL).param("status", "ACTIVE").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].shopId").value(ACTIVE_ID));
    }

    @Test
    @DisplayName("매장명·아이디·연락처로 찾고, 지금 상태 안에서만 찾는다")
    void getApplications_searchesKeywordWithinStatus() throws Exception {
        jdbc.update("UPDATE SHOP SET NAME = '연남 달빛네일', PHONE = '02-555-1861' WHERE SHOP_ID = ?", PENDING_ID);

        searchExpectingOnlyPending("달빛");
        searchExpectingOnlyPending("APPLY9101");
        searchExpectingOnlyPending("555-1861");
        searchExpectingOnlyPending("5551861");
        searchExpectingOnlyPending("  달빛  ");
        mvc.perform(get(URL).param("status", "ACTIVE").param("keyword", "달빛").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("검색어의 % 와 _ 는 글자 그대로 찾고, 빈 검색어는 전체를 돌려준다")
    void getApplications_escapesWildcardsAndIgnoresBlank() throws Exception {
        mvc.perform(get(URL).param("keyword", "%").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get(URL).param("keyword", "   ").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("검색어가 50자를 넘으면 400")
    void getApplications_longKeyword_returnsBadRequest() throws Exception {
        mvc.perform(get(URL).param("keyword", "가".repeat(51)).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isBadRequest());
    }

    private void searchExpectingOnlyPending(String keyword) throws Exception {
        mvc.perform(get(URL).param("keyword", keyword).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].shopId").value(PENDING_ID));
    }

    @Test
    @DisplayName("페이지 크기가 범위를 벗어나면 400")
    void getApplications_invalidSize_returnsBadRequest() throws Exception {
        mvc.perform(get(URL).param("size", "101").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("관리자가 아니면 403, 로그인하지 않으면 401")
    void getApplications_requiresAdmin() throws Exception {
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer CUSTOMER_1"))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer OWNER_1"))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("승인하면 노출 상태가 된다")
    void approve_activatesShop() throws Exception {
        patchApprove(PENDING_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(statusOf(PENDING_ID)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("거절하면 앞뒤 공백을 뺀 사유를 남긴다")
    void reject_savesReason() throws Exception {
        patchReject(PENDING_ID, "{\"reason\":\"  서비스 대상 업종이 아니에요.  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("서비스 대상 업종이 아니에요."));

        assertThat(statusOf(PENDING_ID)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT REJECT_REASON FROM SHOP WHERE SHOP_ID = ?", String.class, PENDING_ID))
                .isEqualTo("서비스 대상 업종이 아니에요.");
    }

    @Test
    @DisplayName("사유가 비어 있으면 거절할 수 없다")
    void reject_blankReason_returnsBadRequest() throws Exception {
        patchReject(PENDING_ID, "{\"reason\":\"   \"}").andExpect(status().isBadRequest());

        assertThat(statusOf(PENDING_ID)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("승인 대기가 아니면 409 ADMIN_002")
    void approve_notPending_returnsConflict() throws Exception {
        patchApprove(ACTIVE_ID)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN_002"));
        patchReject(ACTIVE_ID, "{\"reason\":\"사유\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN_002"));
    }

    @Test
    @DisplayName("없는 신청이면 404 ADMIN_001")
    void approve_unknownShop_returnsNotFound() throws Exception {
        patchApprove(999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADMIN_001"));
    }

    @Test
    @DisplayName("관리자가 아니면 승인할 수 없다")
    void approve_requiresAdmin() throws Exception {
        mvc.perform(patch(URL + "/" + PENDING_ID + "/approve").header(HttpHeaders.AUTHORIZATION, "Bearer OWNER_1"))
                .andExpect(status().isForbidden());

        assertThat(statusOf(PENDING_ID)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("예약이 없는 매장은 승인을 취소하면 거절 상태가 된다")
    void cancelApproval_withoutReservation_rejectsShop() throws Exception {
        patchCancelApproval(ACTIVE_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("잘못 승인했어요."));

        assertThat(statusOf(ACTIVE_ID)).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("앞으로 있을 확정 예약이 있으면 승인을 취소할 수 없다")
    void cancelApproval_withUpcomingReservation_returnsConflict() throws Exception {
        LocalDateTime startsAt = LocalDateTime.now(clock).plusDays(3);
        insertReservation("CONFIRMED", startsAt, startsAt.plusHours(1), startsAt);

        patchCancelApproval(ACTIVE_ID)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN_004"));

        assertThat(statusOf(ACTIVE_ID)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("끝난 예약이나 취소된 예약, 만료된 점유만 있으면 승인을 취소할 수 있다")
    void cancelApproval_withOnlyFinishedReservations_succeeds() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        insertReservation("CONFIRMED", now.minusDays(2), now.minusDays(2).plusHours(1), now);
        insertReservation("CANCELLED", now.plusDays(3), now.plusDays(3).plusHours(1), now);
        insertReservation("HOLD", now.plusDays(4), now.plusDays(4).plusHours(1), now.minusMinutes(1));

        patchCancelApproval(ACTIVE_ID).andExpect(status().isOk());
    }

    @Test
    @DisplayName("승인된 매장이 아니면 승인을 취소할 수 없다")
    void cancelApproval_notApproved_returnsConflict() throws Exception {
        patchCancelApproval(PENDING_ID)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN_003"));
    }

    @Test
    @DisplayName("거절을 취소하면 승인 대기로 돌아가고 사유가 지워진다")
    void cancelRejection_restoresPending() throws Exception {
        patchReject(PENDING_ID, "{\"reason\":\"사유\"}").andExpect(status().isOk());

        mvc.perform(patch(URL + "/" + PENDING_ID + "/cancel-rejection").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.rejectReason").isEmpty());

        assertThat(statusOf(PENDING_ID)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("거절된 신청이 아니면 거절을 취소할 수 없다")
    void cancelRejection_notRejected_returnsConflict() throws Exception {
        mvc.perform(patch(URL + "/" + PENDING_ID + "/cancel-rejection").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN_005"));
    }

    @Test
    @DisplayName("관리자가 아니면 되돌릴 수 없다")
    void cancel_requiresAdmin() throws Exception {
        mvc.perform(patch(URL + "/" + ACTIVE_ID + "/cancel-approval")
                .header(HttpHeaders.AUTHORIZATION, "Bearer OWNER_1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"사유\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(URL + "/" + PENDING_ID + "/cancel-rejection")
                .header(HttpHeaders.AUTHORIZATION, "Bearer CUSTOMER_1"))
                .andExpect(status().isForbidden());
    }

    private ResultActions patchCancelApproval(long shopId) throws Exception {
        return mvc.perform(patch(URL + "/" + shopId + "/cancel-approval")
                .header(HttpHeaders.AUTHORIZATION, ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"잘못 승인했어요.\"}"));
    }

    private ResultActions patchApprove(long shopId) throws Exception {
        return mvc.perform(patch(URL + "/" + shopId + "/approve").header(HttpHeaders.AUTHORIZATION, ADMIN));
    }

    private ResultActions patchReject(long shopId, String body) throws Exception {
        return mvc.perform(patch(URL + "/" + shopId + "/reject")
                .header(HttpHeaders.AUTHORIZATION, ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // 변경 내용을 DB 에 내보낸 뒤 읽어 실제 저장값을 확인한다
    private String statusOf(long shopId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT STATUS FROM SHOP WHERE SHOP_ID = ?", String.class, shopId);
    }

    // 승인된 매장(ACTIVE_ID)의 예약을 넣는다. 고객과 시술은 처음 부를 때 한 번만 만든다
    private void insertReservation(String reservationStatus, LocalDateTime startsAt, LocalDateTime endsAt,
            LocalDateTime holdExpiresAt) {
        jdbc.update("""
                MERGE INTO APP_USER (USER_ID, LOGIN_ID, PASSWORD_HASH, NAME, PHONE, STATUS, ROLE, CREATED_AT, UPDATED_AT)
                KEY (USER_ID) VALUES (9301, 'admintest', 'CHANGE_ME', '고객', '010-0000-9301', 'ACTIVE', 'USER',
                                      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                MERGE INTO TREATMENT (TREATMENT_ID, SHOP_ID, NAME, REGULAR_PRICE, PRICE, LAST_MINUTE_PRICE,
                                      DURATION_MINUTES, TREATMENT_TYPE, STATUS, CREATED_AT, UPDATED_AT)
                KEY (TREATMENT_ID) VALUES (9302, ?, '시술', 30000, 30000, 30000, 60, 'NORMAL', 'ACTIVE',
                                           CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ACTIVE_ID);
        jdbc.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT, APPLIED_PRICE,
                                         DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (NEXT VALUE FOR SEQ_RESERVATION, 9301, 9302, ?, ?, ?, 30000, 20000, ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, reservationStatus, startsAt, endsAt, holdExpiresAt);
    }

    private void insertShop(long shopId, String shopStatus, String createdAt) {
        jdbc.update("""
                INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE, ADDRESS,
                                  LATITUDE, LONGITUDE, PHONE, STATUS, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, ?, 'CHANGE_ME', '신청 매장', '11440', '서울', 37.5, 127.0, '02-000-0000', ?,
                        CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))
                """, shopId, CATEGORY_ID, "apply" + shopId, shopStatus, createdAt, createdAt);
    }
}
