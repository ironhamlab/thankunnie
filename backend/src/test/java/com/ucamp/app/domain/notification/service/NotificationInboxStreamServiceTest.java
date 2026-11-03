package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

class NotificationInboxStreamServiceTest {

    private static final AuthUser CUSTOMER = TestUser.CUSTOMER_1.toAuthUser();
    private static final AuthUser OTHER_CUSTOMER = TestUser.CUSTOMER_2.toAuthUser();
    private static final AuthUser OWNER = TestUser.OWNER_1.toAuthUser();
    private static final AuthUser ADMIN = TestUser.ADMIN.toAuthUser();

    private NotificationInboxService notificationInboxService;
    private NotificationInboxConnections connections;
    private NotificationInboxStreamService notificationInboxStreamService;

    @BeforeEach
    void setUp() {
        notificationInboxService = mock(NotificationInboxService.class);
        connections = new NotificationInboxConnections();
        notificationInboxStreamService = new NotificationInboxStreamService(notificationInboxService, connections);
    }

    @Test
    @DisplayName("연결하면 목록에 남고 빼면 목록에서 사라진다")
    void open_registersAndRemoveOnComplete() {
        given(notificationInboxService.countUnread(CUSTOMER)).willReturn(2L);

        SseEmitter emitter = notificationInboxStreamService.open(CUSTOMER);

        assertThat(connections.count(CUSTOMER.userId())).isEqualTo(1);
        connections.remove(CUSTOMER.userId(), emitter);
        assertThat(connections.count(CUSTOMER.userId())).isZero();
    }

    @Test
    @DisplayName("같은 회원의 연결이 둘이면 뺀 것만 목록에서 사라진다")
    void open_twoConnections_removesOnlyClosedOne() {
        given(notificationInboxService.countUnread(CUSTOMER)).willReturn(1L);
        given(notificationInboxService.countUnread(OTHER_CUSTOMER)).willReturn(0L);

        SseEmitter first = notificationInboxStreamService.open(CUSTOMER);
        SseEmitter second = notificationInboxStreamService.open(CUSTOMER);
        SseEmitter other = notificationInboxStreamService.open(OTHER_CUSTOMER);

        assertThat(connections.count(CUSTOMER.userId())).isEqualTo(2);
        connections.remove(CUSTOMER.userId(), first);
        assertThat(connections.count(CUSTOMER.userId())).isEqualTo(1);
        assertThat(connections.count(OTHER_CUSTOMER.userId())).isEqualTo(1);

        connections.remove(CUSTOMER.userId(), second);
        connections.remove(OTHER_CUSTOMER.userId(), other);
        assertThat(connections.count(CUSTOMER.userId())).isZero();
        assertThat(connections.count(OTHER_CUSTOMER.userId())).isZero();
    }

    @Test
    @DisplayName("사장님 연결은 매장 ID로 남고 같은 숫자의 고객 연결과는 따로다")
    void open_owner_registersShopConnection() {
        given(notificationInboxService.countUnread(CUSTOMER)).willReturn(1L);
        given(notificationInboxService.countUnread(OWNER)).willReturn(2L);

        notificationInboxStreamService.open(CUSTOMER);
        SseEmitter owner = notificationInboxStreamService.open(OWNER);

        assertThat(connections.count(CUSTOMER.userId())).isEqualTo(1);
        assertThat(connections.countShop(OWNER.shopId())).isEqualTo(1);
        assertThat(connections.count(OWNER.userId())).isZero();

        connections.removeShop(OWNER.shopId(), owner);
        assertThat(connections.countShop(OWNER.shopId())).isZero();
        assertThat(connections.count(CUSTOMER.userId())).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자는 연결을 만들지 않는다")
    void open_admin_doesNotRegister() {
        assertThatThrownBy(() -> notificationInboxStreamService.open(ADMIN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(connections.count(ADMIN.userId())).isZero();
        assertThat(connections.countShop(OWNER.shopId())).isZero();
    }
}
