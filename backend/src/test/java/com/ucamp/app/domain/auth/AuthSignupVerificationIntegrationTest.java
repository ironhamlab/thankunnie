package com.ucamp.app.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.time.LocalDate;
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
import com.ucamp.app.domain.auth.service.AuthBusinessVerificationService;
import com.ucamp.app.domain.auth.client.NtsBusinessClient;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;

@SpringBootTest(classes = AuthIntegrationTest.AuthTestConfiguration.class)
@Import({AuthVerificationController.class, AuthPhoneVerificationService.class, AuthBusinessVerificationService.class})
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
    @MockitoBean
    private NtsBusinessClient ntsBusinessClient;

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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_010"));
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
    @DisplayName("이미 가입된 휴대폰 번호는 고객 인증번호 발송과 가입을 차단한다")
    void signupCustomer_duplicatePhone_rejected() throws Exception {
        String id = sendAndVerify();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(customerBody("unnie05", phone, id)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/phone/send").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("phone", phone))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_010"));
        verify(smsClient, times(1)).send(anyString(), anyString());

        // 화면을 거치지 않고 다른 번호로 받은 인증을 써도 가입 번호 기준으로 차단한다.
        String otherId = sendAndVerify("0109876" + PHONE_SEQUENCE.incrementAndGet());
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(customerBody("unnie06", phone, otherId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_010"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM APP_USER WHERE LOGIN_ID = 'unnie06'", Integer.class))
                .isZero();
        // 서비스 사전 검사와 별개로 DB에서도 같은 번호의 저장을 차단한다.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO APP_USER (USER_ID, LOGIN_ID, PASSWORD_HASH, NAME, PHONE, CREATED_AT, UPDATED_AT)
                SELECT USER_ID + 1, 'other-user', PASSWORD_HASH, NAME, PHONE, CREATED_AT, UPDATED_AT
                FROM APP_USER WHERE LOGIN_ID = 'unnie05'
                """))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("UK_APP_USER_PHONE");
    }

    @Test
    @DisplayName("휴대폰 인증 없이 사업자 확인만으로 유선 연락처 매장을 가입한다")
    void signupShop_businessVerifiedWithLandline_succeeds() throws Exception {
        mvc.perform(post("/api/v1/auth/business/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessNumber":"1234567891","representativeName":"테스트 대표","openedOn":"2020-01-01"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.verified").value(true));
        String body = """
                {"loginId":"nailshop","password":"CHANGE_ME-1234","categoryId":9001,
                 "name":"언니네 네일","regionCode":"11680","address":"서울 강남구 테헤란로 1",
                 "latitude":37.4979,"longitude":127.0276,"phone":"02-111-1111",
                 "businessNumber":"1234567891","representativeName":"테스트 대표","openedOn":"2020-01-01"}
                """;
        mvc.perform(post("/api/v1/auth/shops/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("OWNER"));
        assertThat(jdbc.queryForObject("SELECT PHONE FROM SHOP WHERE LOGIN_ID = 'nailshop'", String.class))
                .isEqualTo("02-111-1111");
        assertThat(jdbc.queryForObject("SELECT BUSINESS_NUMBER FROM SHOP WHERE LOGIN_ID = 'nailshop'", String.class))
                .isEqualTo("1234567891");
        verify(ntsBusinessClient, times(2)).validate("1234567891", "테스트 대표", LocalDate.of(2020, 1, 1));
        mvc.perform(post("/api/v1/auth/shops/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("nailshop", "secondshop")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("AUTH_009"));
        // 서비스 사전 검사와 별개로 DB에서도 동일 번호의 저장을 차단한다.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, BUSINESS_NUMBER, NAME,
                    REGION_CODE, ADDRESS, LATITUDE, LONGITUDE, PHONE, CREATED_AT, UPDATED_AT)
                SELECT SHOP_ID + 1, CATEGORY_ID, 'other-owner', PASSWORD_HASH, BUSINESS_NUMBER, NAME,
                    REGION_CODE, ADDRESS, LATITUDE, LONGITUDE, PHONE, CREATED_AT, UPDATED_AT
                FROM SHOP WHERE LOGIN_ID = 'nailshop'
                """))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("UK_SHOP_BUSINESS_NUMBER");
    }

    @Test
    @DisplayName("사업자 사전 확인 후 대표자명을 바꾸면 최종 가입에서 다시 조회하고 저장을 차단한다")
    void signupShop_changedBusinessInformation_rechecksProvider() throws Exception {
        mvc.perform(post("/api/v1/auth/business/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessNumber":"1234567891","representativeName":"테스트 대표","openedOn":"2020-01-01"}
                                """))
                .andExpect(status().isOk());
        doThrow(new BusinessException(ErrorCode.AUTH_BUSINESS_INVALID))
                .when(ntsBusinessClient).validate("1234567891", "다른 대표", LocalDate.of(2020, 1, 1));
        mvc.perform(post("/api/v1/auth/shops/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"changedshop","password":"CHANGE_ME-1234","categoryId":9001,
                                 "name":"언니네 네일","regionCode":"11680","address":"서울 강남구 테헤란로 1",
                                 "latitude":37.4979,"longitude":127.0276,"phone":"02-111-1111",
                                 "businessNumber":"1234567891","representativeName":"다른 대표","openedOn":"2020-01-01"}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("AUTH_007"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SHOP WHERE LOGIN_ID = 'changedshop'", Integer.class)).isZero();
    }

    private String send() throws Exception {
        return send(phone);
    }

    private String send(String targetPhone) throws Exception {
        String response = mvc.perform(post("/api/v1/auth/phone/send").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("phone", targetPhone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.code").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("data").path("verificationId").asText();
    }

    private String sendAndVerify() throws Exception {
        return sendAndVerify(phone);
    }

    private String sendAndVerify(String targetPhone) throws Exception {
        String id = send(targetPhone);
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
