package com.ucamp.app.domain.notification.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 카테고리·지역 구독의 마지막 발송 시각과, 아직 문자로 보내지 않은 이벤트 번호를 메모리에 둔다.
 * 키는 구독 번호다. 서버를 다시 켜면 이 내용은 사라진다.
 * 메서드는 모두 synchronized다. 발송 줄을 만드는 일과 나중에 묶음을 비우는 일이 동시에 건드려도 목록이 깨지지 않게 한다.
 */
@Component
public class CategoryRegionPendingQueue {

    private final Map<Long, Pending> bySubscriptionId = new HashMap<>();

    /**
     * 이 구독의 마지막 발송 시각을 적는다. 아직 안 보낸 이벤트 목록은 지우지 않는다.
     *
     * @param subscriptionId 카테고리·지역 구독 번호
     * @param sentAt         마지막으로 문자를 보낸 시각. 없으면 적지 않는다
     */
    public synchronized void markSent(Long subscriptionId, LocalDateTime sentAt) {
        if (subscriptionId == null || sentAt == null) {
            return;
        }
        pendingOf(subscriptionId).lastSentAt = sentAt;
    }

    /**
     * 아직 안 보낸 이벤트 번호 목록에 넣는다. 같은 번호가 이미 있으면 다시 넣지 않는다.
     *
     * @param subscriptionId 카테고리·지역 구독 번호
     * @param eventId        알림 이벤트 번호
     */
    public synchronized void addPendingEvent(Long subscriptionId, Long eventId) {
        if (subscriptionId == null || eventId == null) {
            return;
        }
        List<Long> eventIds = pendingOf(subscriptionId).eventIds;
        if (eventIds.contains(eventId)) {
            return;
        }
        eventIds.add(eventId);
    }

    /**
     * @param subscriptionId 카테고리·지역 구독 번호
     * @return 마지막 발송 시각. 한 번도 안 보냈거나 구독 번호가 없으면 null
     */
    public synchronized LocalDateTime lastSentAt(Long subscriptionId) {
        Pending pending = bySubscriptionId.get(subscriptionId);
        if (pending == null) {
            return null;
        }
        return pending.lastSentAt;
    }

    /**
     * @param subscriptionId 카테고리·지역 구독 번호
     * @return 아직 안 보낸 이벤트 번호. 넣은 순서 그대로다. 없으면 빈 목록
     */
    public synchronized List<Long> pendingEventIds(Long subscriptionId) {
        Pending pending = bySubscriptionId.get(subscriptionId);
        if (pending == null) {
            return List.of();
        }
        return List.copyOf(pending.eventIds);
    }

    /**
     * @return 메모장에 있는 구독 번호. 없으면 빈 목록
     */
    public synchronized List<Long> subscriptionIds() {
        return List.copyOf(bySubscriptionId.keySet());
    }

    /**
     * 구독마다 적어 둔 마지막 발송 시각과 이벤트 번호를 모두 지운다.
     * 테스트가 다음 케이스를 시작하기 전에 쓴다.
     */
    public synchronized void clear() {
        bySubscriptionId.clear();
    }

    /**
     * 아직 안 보낸 이벤트 번호만 비운다. 마지막 발송 시각은 그대로 둔다.
     *
     * @param subscriptionId 카테고리·지역 구독 번호
     */
    public synchronized void clearPendingEvents(Long subscriptionId) {
        Pending pending = bySubscriptionId.get(subscriptionId);
        if (pending == null) {
            return;
        }
        pending.eventIds.clear();
    }

    private Pending pendingOf(Long subscriptionId) {
        Pending pending = bySubscriptionId.get(subscriptionId);
        if (pending == null) {
            pending = new Pending();
            bySubscriptionId.put(subscriptionId, pending);
        }
        return pending;
    }

    /** 구독 하나에 매달아 두는 값. */
    private static final class Pending {

        private LocalDateTime lastSentAt;

        private final List<Long> eventIds = new ArrayList<>();
    }
}
