package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.repository.NotificationInboxRepository;

class NotificationInboxPublishTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 15, 0);
    private static final AuthUser CUSTOMER = TestUser.CUSTOMER_1.toAuthUser();
    private static final AuthUser OTHER_CUSTOMER = TestUser.CUSTOMER_2.toAuthUser();
    private static final AuthUser OWNER = TestUser.OWNER_1.toAuthUser();
    private static final AuthUser ADMIN = TestUser.ADMIN.toAuthUser();

    private NotificationInboxRepository notificationInboxRepository;
    private NotificationInboxConnections connections;
    private NotificationInboxService notificationInboxService;
    private SseEmitter customerEmitter;
    private SseEmitter otherEmitter;

    @BeforeEach
    void setUp() {
        notificationInboxRepository = mock(NotificationInboxRepository.class);
        connections = new NotificationInboxConnections();
        notificationInboxService = new NotificationInboxService(
                notificationInboxRepository,
                Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST),
                connections);
        customerEmitter = mock(SseEmitter.class);
        otherEmitter = mock(SseEmitter.class);
        connections.add(CUSTOMER.userId(), customerEmitter);
        connections.add(OTHER_CUSTOMER.userId(), otherEmitter);
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("저장이 커밋되면 그 회원 연결에만 새 안 읽은 수를 보낸다")
    void save_afterCommit_sendsUnreadCountToThatUser() throws Exception {
        given(notificationInboxRepository.countByUserIdAndReadAtIsNull(CUSTOMER.userId())).willReturn(4L);
        TransactionSynchronizationManager.initSynchronization();

        notificationInboxService.save(CUSTOMER.userId(), NotificationInboxCategory.RESERVATION,
                "예약이 확정되었어요", "확정 문자", "/my-reservations");

        verify(customerEmitter, never()).send(anyString());
        commit();
        verify(customerEmitter).send("4");
        verify(otherEmitter, never()).send(anyString());
    }

    @Test
    @DisplayName("매장 알림이 커밋되면 그 매장 연결에만 안 읽은 수를 보낸다")
    void saveForShop_afterCommit_sendsUnreadCountOnlyToThatShop() throws Exception {
        SseEmitter shopEmitter = mock(SseEmitter.class);
        connections.addShop(CUSTOMER.userId(), shopEmitter);
        given(notificationInboxRepository.countByShopIdAndReadAtIsNull(CUSTOMER.userId())).willReturn(2L);
        TransactionSynchronizationManager.initSynchronization();

        notificationInboxService.saveForShop(CUSTOMER.userId(), NotificationInboxCategory.RESERVATION,
                "예약이 확정되었어요", "김고객 님 · 젤네일 · 11월 2일 10:00 예약이 확정되었습니다.",
                NotificationInboxService.OWNER_BOOKINGS_PATH);

        verify(shopEmitter, never()).send(anyString());
        commit();
        verify(shopEmitter).send("2");
        verify(customerEmitter, never()).send(anyString());
        verify(otherEmitter, never()).send(anyString());
    }

    @Test
    @DisplayName("저장이 취소되면 안 읽은 수를 보내지 않는다")
    void save_withoutCommit_doesNotSend() throws Exception {
        given(notificationInboxRepository.countByUserIdAndReadAtIsNull(CUSTOMER.userId())).willReturn(4L);
        TransactionSynchronizationManager.initSynchronization();

        notificationInboxService.save(CUSTOMER.userId(), NotificationInboxCategory.INTEREST,
                "임박 알림", "임박 문자", "/shops/1");

        verify(customerEmitter, never()).send(anyString());
    }

    @Test
    @DisplayName("모두 읽음이 커밋되면 그 회원 연결에 0을 보낸다")
    void markAllRead_afterCommit_sendsZero() throws Exception {
        given(notificationInboxRepository.findByUserIdAndReadAtIsNull(CUSTOMER.userId())).willReturn(List.of());
        given(notificationInboxRepository.countByUserIdAndReadAtIsNull(CUSTOMER.userId())).willReturn(0L);
        TransactionSynchronizationManager.initSynchronization();

        assertThat(notificationInboxService.markAllRead(CUSTOMER)).isZero();

        verify(customerEmitter, never()).send(anyString());
        commit();
        verify(customerEmitter).send("0");
        verify(otherEmitter, never()).send(anyString());
    }

    @Test
    @DisplayName("사장님 모두 읽음은 매장 연결에만 0을 보낸다")
    void markAllRead_owner_sendsZeroToShopOnly() throws Exception {
        SseEmitter shopEmitter = mock(SseEmitter.class);
        connections.addShop(OWNER.shopId(), shopEmitter);
        given(notificationInboxRepository.findByShopIdAndReadAtIsNull(OWNER.shopId())).willReturn(List.of());
        given(notificationInboxRepository.countByShopIdAndReadAtIsNull(OWNER.shopId())).willReturn(0L);
        TransactionSynchronizationManager.initSynchronization();

        assertThat(notificationInboxService.markAllRead(OWNER)).isZero();

        verify(shopEmitter, never()).send(anyString());
        commit();
        verify(shopEmitter).send("0");
        verify(customerEmitter, never()).send(anyString());
        verify(otherEmitter, never()).send(anyString());
    }

    @Test
    @DisplayName("관리자는 모두 읽음 숫자를 보내지 않는다")
    void markAllRead_admin_doesNotSend() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> notificationInboxService.markAllRead(ADMIN))
                .isInstanceOf(BusinessException.class);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verify(customerEmitter, never()).send(anyString());
    }

    @Test
    @DisplayName("보내다 끊긴 연결만 목록에서 빼고 나머지에는 보낸다")
    void save_brokenConnection_removesOnlyBrokenOne() throws Exception {
        SseEmitter broken = mock(SseEmitter.class);
        doThrow(new IOException("closed")).when(broken).send(anyString());
        connections.add(CUSTOMER.userId(), broken);
        given(notificationInboxRepository.countByUserIdAndReadAtIsNull(CUSTOMER.userId())).willReturn(3L);

        notificationInboxService.save(CUSTOMER.userId(), NotificationInboxCategory.RESERVATION,
                "예약이 확정되었어요", "확정 문자", "/my-reservations");

        verify(customerEmitter).send("3");
        verify(broken).send("3");
        assertThat(connections.count(CUSTOMER.userId())).isEqualTo(1);
        verify(otherEmitter, never()).send(anyString());
    }

    @Test
    @DisplayName("연결 유지 신호는 모든 연결에 보내고 끊긴 연결만 뺀다")
    void sendHeartbeat_removesBrokenConnection() throws Exception {
        doThrow(new IOException("closed")).when(otherEmitter).send(any(SseEventBuilder.class));

        connections.sendHeartbeat();

        verify(customerEmitter).send(any(SseEventBuilder.class));
        verify(customerEmitter, never()).send(anyString());
        assertThat(connections.count(CUSTOMER.userId())).isEqualTo(1);
        assertThat(connections.count(OTHER_CUSTOMER.userId())).isZero();
    }

    private static void commit() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }
}
