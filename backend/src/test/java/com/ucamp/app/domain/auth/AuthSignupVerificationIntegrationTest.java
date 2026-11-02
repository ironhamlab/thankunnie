package com.ucamp.app.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.auth.controller.AuthVerificationController;
import com.ucamp.app.domain.auth.service.AuthPhoneVerificationService;
import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;

@SpringBootTest(classes = AuthIntegrationTest.AuthTestConfiguration.class)
@Import({AuthVerificationController.class, AuthPhoneVerificationService.class})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "auth-test"})
@Transactional
class AuthSignupVerificationIntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1000);
    private final AtomicReference<String> sentCode = new AtomicReference<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private SolapiSmsClient smsClient;

    private String phone;

    @BeforeEach
    void setUp() {
        phone = "0101234" + PHONE_SEQUENCE.incrementAndGet();
        jdbc.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (9001, '네일')");
        jdbc.update("""
                INSERT INTO TERMS (TERMS_ID, CODE, VERSION, TITLE, IS_REQUIRED, CREATED_AT, UPDATED_AT)
                VALUES (9101, 'SERVICE', 1, '서비스 약관', 'Y', TIMESTAMP '2026-10-01 09:00:00',
                        TIMESTAMP '2026-10-01 09:00:00')
                """);
        when(smsClient.send(anyString(), anyString())).thenAnswer(invocation -> {
            var matcher = Pattern.compile("\\[([0-9]{6})\\]").matcher(invocation.<String>getArgument(1));
            assertThat(matcher.find()).isTrue();
            sentCode.set(matcher.group(1));
            return SmsSendResult.success("test-message");
        });
    }

    @Test
    @DisplayName("문자 발송만 하고 인증하지 않으면 가입과 DB 저장을 차단한다")
    void signupCustomer_unverified_doesNotSave() throws Exception {
        String id = send();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(customerBody("unnie01", phone, id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM APP_USER WHERE LOGIN_ID = 'unnie01'", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("문자 인증 후 가입에 성공하고 같은 인증으로 다른 계정을 만들 수 없다")
    void signupCustomer_verified_succeedsAndRejectsReuse() throws Exception {
        String id = sendAndVerify();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(customerBody("unnie02", phone, id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(customerBody("unnie03", phone, id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        mvc.perform(post("/api/v1/auth/login-id/check").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"unnie02","accountType":"customer"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(false));
        mvc.perform(post("/api/v1/auth/login-id/check").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"unnie02","accountType":"shop"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("인증 후 가입 전화번호를 바꾸면 가입을 차단한다")
    void signupCustomer_changedPhone_rejectsRequest() throws Exception {
        String id = sendAndVerify();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(customerBody("unnie04", "01099998888", id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("매장 유선 연락처와 별개인 가입자 휴대폰 인증으로 매장 가입한다")
    void signupShop_verifiedMobileAndLandline_succeeds() throws Exception {
        String id = sendAndVerify();
        String body = """
                {"loginId":"nailshop","password":"CHANGE_ME-1234","categoryId":9001,
                 "name":"언니네 네일","regionCode":"11680","address":"서울 강남구 테헤란로 1",
                 "latitude":37.4979,"longitude":127.0276,"phone":"02-111-1111",
                 "verificationPhone":"%s","phoneVerificationId":"%s"}
                """.formatted(phone, id);
        mvc.perform(post("/api/v1/auth/shops/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("OWNER"));
        assertThat(jdbc.queryForObject("SELECT PHONE FROM SHOP WHERE LOGIN_ID = 'nailshop'", String.class))
                .isEqualTo("02-111-1111");
    }

    private String send() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/phone/send").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("phone", phone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.code").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("data").path("verificationId").asText();
    }

    private String sendAndVerify() throws Exception {
        String id = send();
        mvc.perform(post("/api/v1/auth/phone/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("verificationId", id, "code", sentCode.get()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true));
        return id;
    }

    private String customerBody(String loginId, String customerPhone, String verificationId) throws Exception {
        return mapper.writeValueAsString(Map.of("loginId", loginId, "password", "CHANGE_ME-1234",
                "name", "김언니", "phone", customerPhone, "agreedTerms", List.of("SERVICE"),
                "phoneVerificationId", verificationId));
    }
}
