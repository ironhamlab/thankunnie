package com.ucamp.app.domain.auth.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.auth.service.AuthPhoneVerificationService;
import com.ucamp.app.domain.auth.service.AuthService;
import com.ucamp.app.domain.auth.service.AuthBusinessVerificationService;

@WebMvcTest(controllers = AuthVerificationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AuthVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthPhoneVerificationService verificationService;

    @MockitoBean
    private AuthBusinessVerificationService businessVerificationService;

    @Test
    @DisplayName("비로그인 상태에서 고객 아이디 중복 확인 결과를 받을 수 있다")
    void checkLoginId_anonymous_returnsAvailability() throws Exception {
        when(authService.isLoginIdAvailable("unnie01", "customer")).thenReturn(true);
        mockMvc.perform(post("/api/v1/auth/login-id/check").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"unnie01","accountType":"customer"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("잘못된 아이디 형식과 계정 유형을 서비스 호출 전에 거절한다")
    void checkLoginId_invalidInput_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login-id/check").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"AB","accountType":"admin"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("잘못된 휴대폰 번호로 문자를 발송하지 않는다")
    void send_invalidPhone_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/phone/send").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"02-123-4567"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verificationService);
    }

    @Test
    @DisplayName("매장 가입 인증번호는 휴대폰 번호 중복을 검사하지 않고 발송한다")
    void send_shop_skipsDuplicatePhoneCheck() throws Exception {
        mockMvc.perform(post("/api/v1/auth/phone/send").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"01012345678","accountType":"shop"}
                                """))
                .andExpect(status().isOk());
        verify(authService, never()).validatePhoneAvailable("01012345678");
        verify(verificationService).send("01012345678");
    }

    @Test
    @DisplayName("고객 가입 인증번호는 휴대폰 번호 중복을 먼저 검사한다")
    void send_customer_checksDuplicatePhone() throws Exception {
        doThrow(new BusinessException(ErrorCode.AUTH_DUPLICATE_PHONE))
                .when(authService).validatePhoneAvailable("01012345678");
        mockMvc.perform(post("/api/v1/auth/phone/send").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"01012345678"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_010"));
        verifyNoInteractions(verificationService);
    }

    @Test
    @DisplayName("발송 제한은 429와 인증 전용 오류 코드로 반환한다")
    void send_rateLimited_returnsTooManyRequests() throws Exception {
        when(verificationService.send("01012345678"))
                .thenThrow(new BusinessException(ErrorCode.AUTH_PHONE_SEND_LIMITED, "60초 후 재시도해 주세요."));
        mockMvc.perform(post("/api/v1/auth/phone/send").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"01012345678"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"))
                .andExpect(jsonPath("$.error.message").value("60초 후 재시도해 주세요."));
    }

    @Test
    @DisplayName("6자리 숫자가 아닌 인증번호는 거절한다")
    void verify_invalidCode_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/phone/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"verificationId":"ce79983d-c4b3-4e64-b8eb-bbcd32206e17","code":"123"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verificationService);
    }
}
