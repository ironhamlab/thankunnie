package com.ucamp.app.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.StubAuthFilter;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionCreateRequest;
import com.ucamp.app.domain.notification.dto.response.NotificationSubscriptionResponse;
import com.ucamp.app.domain.notification.entity.SubscriptionStatus;
import com.ucamp.app.domain.notification.entity.SubscriptionType;
import com.ucamp.app.domain.notification.service.NotificationService;

@WebMvcTest(controllers = NotificationController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class NotificationControllerTest {

    private static final String SUBSCRIPTIONS_URL = "/api/v1/notifications/subscriptions";
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-23T14:00:00+09:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-10-23T18:00:00+09:00");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @DisplayName("업장 구독 등록에 성공하면 201 과 구독 정보를 반환한다")
    void subscribe_validStoreRequest_returnsCreated() throws Exception {
        given(notificationService.subscribe(any(AuthUser.class), any(NotificationSubscriptionCreateRequest.class)))
                .willReturn(storeResponse());

        mockMvc.perform(post(SUBSCRIPTIONS_URL)
                        .header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subscriptionType":"STORE","shopId":1,
                                 "startTime":"2026-10-23T14:00:00+09:00","endTime":"2026-10-23T18:00:00+09:00"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subscriptionId").value(1))
                .andExpect(jsonPath("$.data.subscriptionType").value("STORE"))
                .andExpect(jsonPath("$.data.startTime").value("2026-10-23T14:00:00+09:00"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("구독 유형이 없으면 400 을 반환한다")
    void subscribe_missingType_returnsBadRequest() throws Exception {
        mockMvc.perform(post(SUBSCRIPTIONS_URL)
                        .header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shopId":1,"startTime":"2026-10-23T14:00:00+09:00","endTime":"2026-10-23T18:00:00+09:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verify(notificationService, never()).subscribe(any(), any());
    }

    @Test
    @DisplayName("비로그인이면 구독을 등록할 수 없다")
    void subscribe_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(post(SUBSCRIPTIONS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subscriptionType":"STORE","shopId":1,
                                 "startTime":"2026-10-23T14:00:00+09:00","endTime":"2026-10-23T18:00:00+09:00"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("내 구독 목록을 조회한다")
    void getMySubscriptions_customer_returnsList() throws Exception {
        given(notificationService.getMySubscriptions(any(AuthUser.class))).willReturn(List.of(storeResponse()));

        mockMvc.perform(get(SUBSCRIPTIONS_URL).header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].subscriptionId").value(1))
                .andExpect(jsonPath("$.data[0].shopId").value(1));
    }

    @Test
    @DisplayName("구독 해제에 성공하면 본문 없이 204 를 반환한다")
    void unsubscribe_own_returnsNoContent() throws Exception {
        mockMvc.perform(delete(SUBSCRIPTIONS_URL + "/1").header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("없는 구독을 해제하면 404 를 반환한다")
    void unsubscribe_notFound_returnsNotFound() throws Exception {
        willThrow(new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND))
                .given(notificationService).unsubscribe(any(AuthUser.class), eq(99L));

        mockMvc.perform(delete(SUBSCRIPTIONS_URL + "/99").header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_001"));
    }

    private static NotificationSubscriptionResponse storeResponse() {
        return new NotificationSubscriptionResponse(1L, SubscriptionType.STORE, 1L, null, null,
                START, END, SubscriptionStatus.ACTIVE);
    }
}
