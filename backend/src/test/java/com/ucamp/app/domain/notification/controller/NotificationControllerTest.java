package com.ucamp.app.domain.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.StubAuthFilter;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionCreateRequest;
import com.ucamp.app.domain.notification.dto.response.NotificationInboxResponse;
import com.ucamp.app.domain.notification.dto.response.NotificationSubscriptionResponse;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.entity.SubscriptionStatus;
import com.ucamp.app.domain.notification.entity.SubscriptionType;
import com.ucamp.app.domain.notification.service.NotificationInboxConnections;
import com.ucamp.app.domain.notification.service.NotificationInboxService;
import com.ucamp.app.domain.notification.service.NotificationInboxStreamService;
import com.ucamp.app.domain.notification.service.NotificationService;

@WebMvcTest(controllers = NotificationController.class)
@Import({SecurityConfig.class, NotificationInboxStreamService.class, NotificationInboxConnections.class})
@ActiveProfiles("test")
class NotificationControllerTest {

    private static final String SUBSCRIPTIONS_URL = "/api/v1/notifications/subscriptions";
    private static final String INBOX_URL = "/api/v1/notifications/inbox";
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-23T14:00:00+09:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-10-23T18:00:00+09:00");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationInboxService notificationInboxService;

    @Autowired
    private NotificationInboxConnections notificationInboxConnections;

    @AfterEach
    void closeStreams() {
        notificationInboxConnections.completeAll();
    }

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

    @Test
    @DisplayName("받은 알림 전체를 조회한다")
    void getInbox_customer_returnsList() throws Exception {
        given(notificationInboxService.getMyInbox(any(AuthUser.class), eq(null)))
                .willReturn(List.of(inboxResponse()));

        mockMvc.perform(get(INBOX_URL).header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].inboxId").value(10))
                .andExpect(jsonPath("$.data[0].category").value("RESERVATION"))
                .andExpect(jsonPath("$.data[0].readAt").value("2026-10-31T15:00:00+09:00"));
    }

    @Test
    @DisplayName("받은 알림을 관심만 조회한다")
    void getInbox_interest_passesCategory() throws Exception {
        given(notificationInboxService.getMyInbox(any(AuthUser.class), eq(NotificationInboxCategory.INTEREST)))
                .willReturn(List.of());

        mockMvc.perform(get(INBOX_URL)
                        .header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1")
                        .param("category", "INTEREST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("받은 알림 종류가 아니면 400 을 반환한다")
    void getInbox_unknownCategory_returnsBadRequest() throws Exception {
        mockMvc.perform(get(INBOX_URL)
                        .header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1")
                        .param("category", "SALE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verify(notificationInboxService, never()).getMyInbox(any(), any());
    }

    @Test
    @DisplayName("비로그인이면 받은 알림을 조회할 수 없다")
    void getInbox_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(INBOX_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("스트림에 연결하면 지금 안 읽은 수를 한 번 보낸다")
    void stream_customer_sendsUnreadCount() throws Exception {
        given(notificationInboxService.countUnread(any(AuthUser.class))).willReturn(2L);

        MvcResult result = mockMvc.perform(get(INBOX_URL + "/stream")
                        .header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("data:2");
        assertThat(notificationInboxConnections.count(TestUser.CUSTOMER_1.toAuthUser().userId())).isEqualTo(1);
        notificationInboxConnections.completeAll();
        mockMvc.perform(asyncDispatch(result));
        assertThat(notificationInboxConnections.count(TestUser.CUSTOMER_1.toAuthUser().userId())).isZero();
    }

    @Test
    @DisplayName("비로그인은 알림 스트림에 연결할 수 없다")
    void stream_anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(INBOX_URL + "/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        verify(notificationInboxService, never()).countUnread(any());
    }

    @Test
    @DisplayName("매장은 알림 스트림에 연결할 수 없다")
    void stream_owner_returnsForbidden() throws Exception {
        willThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .given(notificationInboxService).countUnread(any(AuthUser.class));

        mockMvc.perform(get(INBOX_URL + "/stream")
                        .header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_403"));
        assertThat(notificationInboxConnections.count(TestUser.OWNER_1.toAuthUser().userId())).isZero();
    }

    @Test
    @DisplayName("모두 읽음은 처리 후 안 읽은 수를 반환한다")
    void markAllRead_customer_returnsUnreadCount() throws Exception {
        given(notificationInboxService.markAllRead(any(AuthUser.class))).willReturn(0L);

        mockMvc.perform(patch(INBOX_URL + "/read-all").header(StubAuthFilter.HEADER_NAME, "CUSTOMER_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("매장은 받은 알림을 조회할 수 없다")
    void getInbox_owner_returnsForbidden() throws Exception {
        willThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .given(notificationInboxService).getMyInbox(any(AuthUser.class), any());

        mockMvc.perform(get(INBOX_URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_403"));
    }

    private static NotificationInboxResponse inboxResponse() {
        return new NotificationInboxResponse(
                10L,
                NotificationInboxCategory.RESERVATION,
                "예약이 확정되었어요",
                "[땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 확정되었습니다.",
                "/my-reservations",
                OffsetDateTime.parse("2026-10-31T15:00:00+09:00"),
                OffsetDateTime.parse("2026-10-31T14:00:00+09:00"));
    }

    private static NotificationSubscriptionResponse storeResponse() {
        return new NotificationSubscriptionResponse(1L, SubscriptionType.STORE, 1L, null, null,
                START, END, SubscriptionStatus.ACTIVE);
    }
}
