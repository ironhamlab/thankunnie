package com.ucamp.app.domain.notification.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

/**
 * 로그인한 고객의 알림 스트림 연결을 메모리에 둔다.
 * 같은 회원이 탭을 여러 개 열면 연결도 여러 개다. 끊기면 그 연결만 목록에서 뺀다.
 */
@Slf4j
@Component
public class NotificationInboxConnections {

    private final Map<Long, List<SseEmitter>> connections = new HashMap<>();

    /**
     * 연결을 회원 목록에 넣는다. 완료·시간 초과·오류가 나면 목록에서 뺀다.
     *
     * @param userId  회원 ID
     * @param emitter 이번 연결
     */
    public synchronized void add(Long userId, SseEmitter emitter) {
        List<SseEmitter> mine = connections.computeIfAbsent(userId, id -> new ArrayList<>());
        mine.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
    }

    /**
     * 끊긴 연결을 회원 목록에서 뺀다. 남은 연결이 없으면 회원 항목도 지운다.
     *
     * @param userId  회원 ID
     * @param emitter 끊긴 연결
     */
    public synchronized void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> mine = connections.get(userId);
        if (mine == null) {
            return;
        }
        mine.remove(emitter);
        if (mine.isEmpty()) {
            connections.remove(userId);
        }
    }

    /**
     * 이 회원이 열어 둔 연결마다 안 읽은 수를 보낸다. 연결이 없으면 아무것도 하지 않는다.
     * 보내다 끊긴 연결은 목록에서 뺀다. 다른 회원 연결에는 보내지 않는다.
     *
     * @param userId      회원 ID
     * @param unreadCount 지금 안 읽은 수
     */
    public void send(Long userId, long unreadCount) {
        for (SseEmitter emitter : copy(userId)) {
            try {
                emitter.send(String.valueOf(unreadCount));
            } catch (IOException | IllegalStateException exception) {
                log.warn("알림 개수 전송 실패 userId={}", userId, exception);
                remove(userId, emitter);
                completeQuietly(emitter);
            }
        }
    }

    /**
     * 이 회원이 지금 열어 둔 연결 수다.
     *
     * @param userId 회원 ID
     * @return 연결 수. 없으면 0
     */
    public synchronized int count(Long userId) {
        List<SseEmitter> mine = connections.get(userId);
        if (mine == null) {
            return 0;
        }
        return mine.size();
    }

    /**
     * 모아 둔 연결을 모두 끊는다. 테스트가 끝난 뒤 스트림을 정리할 때 쓴다.
     */
    public synchronized void completeAll() {
        List<SseEmitter> all = new ArrayList<>();
        for (List<SseEmitter> mine : connections.values()) {
            all.addAll(mine);
        }
        for (SseEmitter emitter : all) {
            emitter.complete();
        }
    }

    private synchronized List<SseEmitter> copy(Long userId) {
        List<SseEmitter> mine = connections.get(userId);
        if (mine == null) {
            return List.of();
        }
        return new ArrayList<>(mine);
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // 이미 끝난 연결이다.
        }
    }
}
