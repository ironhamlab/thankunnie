package com.ucamp.app.domain.notification.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.domain.notification.service.NotificationInboxConnections;

/**
 * 알림 스트림이 배포 Nginx의 읽기 제한 시간 안에 끊기지 않게 연결 유지 신호를 보낸다.
 * 신호는 주석이라 안 읽은 수와 화면 알림을 바꾸지 않는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationInboxHeartbeatScheduler {

    private final NotificationInboxConnections connections;

    /**
     * 직전 실행이 끝나고 30초 뒤 다시 실행한다.
     * Nginx 읽기 제한은 300초이므로 그보다 짧게 보낸다.
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void sendHeartbeat() {
        connections.sendHeartbeat();
    }
}
