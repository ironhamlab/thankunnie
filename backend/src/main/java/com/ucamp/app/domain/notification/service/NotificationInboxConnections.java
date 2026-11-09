package com.ucamp.app.domain.notification.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.common.auth.UserRole;

/**
 * 로그인한 고객·사장님의 알림 스트림 연결을 메모리에 둔다.
 * 키는 역할과 ID다. 고객은 회원 ID, 사장님은 매장 ID를 쓴다.
 * 숫자가 같아도 역할이 다르면 다른 연결이라 서로 보내지 않는다.
 * 같은 사람이 탭을 여러 개 열면 연결도 여러 개다. 끊기면 그 연결만 목록에서 뺀다.
 */
@Slf4j
@Component
public class NotificationInboxConnections {

    private final Map<StreamKey, List<SseEmitter>> connections = new HashMap<>();

    /**
     * 고객 화면 연결을 넣는다. 완료·시간 초과·오류가 나면 목록에서 뺀다.
     *
     * @param userId  회원 ID
     * @param emitter 이번 연결
     */
    public void add(Long userId, SseEmitter emitter) {
        add(customerKey(userId), emitter);
    }

    /**
     * 사장님 화면 연결을 넣는다. 완료·시간 초과·오류가 나면 목록에서 뺀다.
     *
     * @param shopId  매장 ID
     * @param emitter 이번 연결
     */
    public void addShop(Long shopId, SseEmitter emitter) {
        add(ownerKey(shopId), emitter);
    }

    /**
     * 끊긴 고객 연결을 목록에서 뺀다.
     *
     * @param userId  회원 ID
     * @param emitter 끊긴 연결
     */
    public void remove(Long userId, SseEmitter emitter) {
        remove(customerKey(userId), emitter);
    }

    /**
     * 끊긴 사장님 연결을 목록에서 뺀다.
     *
     * @param shopId  매장 ID
     * @param emitter 끊긴 연결
     */
    public void removeShop(Long shopId, SseEmitter emitter) {
        remove(ownerKey(shopId), emitter);
    }

    /**
     * 이 회원이 열어 둔 연결마다 안 읽은 수를 보낸다. 연결이 없으면 아무것도 하지 않는다.
     * 같은 숫자의 매장 연결에는 보내지 않는다.
     *
     * @param userId      회원 ID
     * @param unreadCount 지금 안 읽은 수
     */
    public void send(Long userId, long unreadCount) {
        send(customerKey(userId), unreadCount);
    }

    /**
     * 이 매장이 열어 둔 연결마다 안 읽은 수를 보낸다. 연결이 없으면 아무것도 하지 않는다.
     * 같은 숫자의 회원 연결에는 보내지 않는다.
     *
     * @param shopId      매장 ID
     * @param unreadCount 지금 안 읽은 수
     */
    public void sendToShop(Long shopId, long unreadCount) {
        send(ownerKey(shopId), unreadCount);
    }

    /**
     * 이 회원이 지금 열어 둔 연결 수다.
     *
     * @param userId 회원 ID
     * @return 연결 수. 없으면 0
     */
    public int count(Long userId) {
        return count(customerKey(userId));
    }

    /**
     * 이 매장이 지금 열어 둔 연결 수다.
     *
     * @param shopId 매장 ID
     * @return 연결 수. 없으면 0
     */
    public int countShop(Long shopId) {
        return count(ownerKey(shopId));
    }

    /**
     * 열려 있는 연결마다 연결 유지용 주석을 보낸다.
     * 알림 개수가 아니라서 화면의 뱃지나 토스트는 바뀌지 않는다.
     * 배포 Nginx는 300초 동안 데이터가 없으면 연결을 끊으므로, 그보다 짧게 보낸다.
     * 보내다 끊긴 연결은 목록에서 뺀다.
     */
    public void sendHeartbeat() {
        for (Map.Entry<StreamKey, List<SseEmitter>> entry : copyAll()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException exception) {
                    log.warn("알림 연결 유지 신호 전송 실패 role={} id={}",
                            entry.getKey().role(), entry.getKey().id(), exception);
                    remove(entry.getKey(), emitter);
                    completeQuietly(emitter);
                }
            }
        }
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

    private synchronized void add(StreamKey key, SseEmitter emitter) {
        List<SseEmitter> mine = connections.computeIfAbsent(key, ignored -> new ArrayList<>());
        mine.add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(error -> remove(key, emitter));
    }

    private synchronized void remove(StreamKey key, SseEmitter emitter) {
        List<SseEmitter> mine = connections.get(key);
        if (mine == null) {
            return;
        }
        mine.remove(emitter);
        if (mine.isEmpty()) {
            connections.remove(key);
        }
    }

    private void send(StreamKey key, long unreadCount) {
        for (SseEmitter emitter : copy(key)) {
            try {
                emitter.send(String.valueOf(unreadCount));
            } catch (IOException | IllegalStateException exception) {
                log.warn("알림 개수 전송 실패 role={} id={}", key.role(), key.id(), exception);
                remove(key, emitter);
                completeQuietly(emitter);
            }
        }
    }

    private synchronized int count(StreamKey key) {
        List<SseEmitter> mine = connections.get(key);
        if (mine == null) {
            return 0;
        }
        return mine.size();
    }

    private synchronized List<SseEmitter> copy(StreamKey key) {
        List<SseEmitter> mine = connections.get(key);
        if (mine == null) {
            return List.of();
        }
        return new ArrayList<>(mine);
    }

    private synchronized List<Map.Entry<StreamKey, List<SseEmitter>>> copyAll() {
        List<Map.Entry<StreamKey, List<SseEmitter>>> copy = new ArrayList<>();
        for (Map.Entry<StreamKey, List<SseEmitter>> entry : connections.entrySet()) {
            copy.add(Map.entry(entry.getKey(), new ArrayList<>(entry.getValue())));
        }
        return copy;
    }

    private static StreamKey customerKey(Long userId) {
        return new StreamKey(UserRole.CUSTOMER, userId);
    }

    private static StreamKey ownerKey(Long shopId) {
        return new StreamKey(UserRole.OWNER, shopId);
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // 이미 끝난 연결이다.
        }
    }

    /**
     * 스트림 연결 키. 역할과 ID를 같이 둔다.
     *
     * @param role 고객 또는 사장님
     * @param id   고객이면 회원 ID, 사장님이면 매장 ID
     */
    public record StreamKey(UserRole role, Long id) {
    }
}
