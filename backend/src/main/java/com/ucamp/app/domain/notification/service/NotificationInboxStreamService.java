package com.ucamp.app.domain.notification.service;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;

/**
 * 고객·사장님의 알림 스트림을 연다.
 * 연결되면 지금 안 읽은 수를 한 번 보내고, 연결은 메모리 목록에 남겨 둔다.
 * 고객은 회원 ID, 사장님은 매장 ID로 연결을 구분한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationInboxStreamService {

    /** 0 이하면 서버가 시간을 재서 스트림을 끊지 않는다. */
    private static final long NO_TIMEOUT_MILLIS = 0L;

    private final NotificationInboxService notificationInboxService;
    private final NotificationInboxConnections connections;

    /**
     * 고객 또는 사장님의 알림 스트림을 연다. 그 외 역할은 연결을 만들지 않는다.
     * 첫 이벤트 data 는 지금 안 읽은 수다.
     *
     * @param loginUser 로그인한 고객 또는 사장님
     * @return 열린 스트림
     */
    public SseEmitter open(AuthUser loginUser) {
        loginUser.requireRole(UserRole.CUSTOMER, UserRole.OWNER);
        long unreadCount = notificationInboxService.countUnread(loginUser);
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT_MILLIS);
        if (loginUser.hasRole(UserRole.OWNER)) {
            connections.addShop(loginUser.shopId(), emitter);
        } else {
            connections.add(loginUser.userId(), emitter);
        }
        try {
            emitter.send(String.valueOf(unreadCount));
        } catch (IOException exception) {
            remove(loginUser, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private void remove(AuthUser loginUser, SseEmitter emitter) {
        if (loginUser.hasRole(UserRole.OWNER)) {
            connections.removeShop(loginUser.shopId(), emitter);
            return;
        }
        connections.remove(loginUser.userId(), emitter);
    }
}
