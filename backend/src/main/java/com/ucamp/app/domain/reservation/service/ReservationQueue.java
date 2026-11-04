package com.ucamp.app.domain.reservation.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import com.ucamp.app.domain.vacancy.policy.SalePolicy;

/**
 * 빈자리 하나(매장 + 시작~끝 일시)에 선 줄. 먼저 선 순서대로 차례가 온다.
 * 시술이 달라도 시작·끝 시각이 같으면 같은 시간을 두고 다투므로 한 줄에 선다.
 * 여러 요청 스레드가 같은 줄을 함께 바꾸므로 모든 메서드를 이 객체로 잠근다.
 * 서버 메모리에만 있어 재시작하면 비워진다. 예약 자체는 DB 점유(HOLD)가 지키므로 순서만 다시 선다.
 */
final class ReservationQueue {

    /** 대기 화면이 이 시간 동안 순서를 다시 묻지 않으면 떠난 것으로 보고 줄에서 뺀다 */
    static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(15);
    /** 차례가 온 뒤 이 시간 안에 예약하지 않으면 다음 사람에게 넘긴다 */
    static final Duration TURN_TIMEOUT = Duration.ofSeconds(15);
    /** 같은 줄의 여러 사람이 순서를 물어도 빈자리 상태는 이 시간에 한 번만 DB 에서 읽는다 */
    static final Duration STATE_CACHE_TTL = Duration.ofSeconds(1);

    /** 회원 ID → 마지막으로 순서를 물은 시각. 넣은 순서가 줄 순서다 */
    private final LinkedHashMap<Long, LocalDateTime> lastSeenByUser = new LinkedHashMap<>();
    /** 회원 ID → 줄에 처음 섰을 때 받은 번호표 */
    private final Map<Long, Long> ticketByUser = new HashMap<>();
    private Long turnUserId;
    private LocalDateTime turnStartedAt;
    private SlotState cachedState;
    private LocalDateTime stateCheckedAt;
    private LocalDateTime lastAccessedAt;
    /**
     * 모든 줄이 함께 쓰는 번호표 발급기. 겹치는 시간의 서로 다른 줄끼리 누가 먼저 섰는지 비교하는 데 쓰므로
     * 줄마다 따로 세지 않고 줄을 만드는 쪽(ReservationQueueService)이 하나를 나눠 준다.
     */
    private final LongSupplier tickets;

    ReservationQueue(LocalDateTime now, LongSupplier tickets) {
        this.lastAccessedAt = now;
        this.tickets = tickets;
    }

    synchronized void touch(LocalDateTime now) {
        lastAccessedAt = now;
    }

    /**
     * @return 아직 유효한 빈자리 상태, 오래됐거나 없으면 null
     */
    synchronized SlotState cachedState(LocalDateTime now) {
        if (cachedState == null || stateCheckedAt.plus(STATE_CACHE_TTL).isBefore(now)) {
            return null;
        }
        return cachedState;
    }

    synchronized void cacheState(SlotState state, LocalDateTime now) {
        cachedState = state;
        stateCheckedAt = now;
    }

    /** 점유가 생기거나 누가 나가 빈자리 상태가 바뀌었을 수 있으면 다음 조회에서 DB 를 다시 읽게 한다 */
    synchronized void invalidateState() {
        cachedState = null;
    }

    /**
     * 줄에 서거나(이미 서 있으면 순서 유지) 순서를 다시 묻는다.
     *
     * @param held 다른 사람이 이 시간을 점유하고 결제 중인지
     * @return 내 앞 사람 수와 내 차례 정보
     */
    synchronized Position join(Long userId, boolean held, LocalDateTime now) {
        lastAccessedAt = now;
        prune(now);
        // 이미 있는 키에 다시 넣으면 LinkedHashMap 은 순서를 바꾸지 않는다
        lastSeenByUser.put(userId, now);
        ticketByUser.computeIfAbsent(userId, id -> tickets.getAsLong());
        int aheadCount = indexOf(userId);
        if (held) {
            // 앞사람이 결제 중이면 아무도 차례가 아니다. 점유가 풀리면 맨 앞 사람의 차례가 새로 시작된다
            turnUserId = null;
            return Position.waiting(aheadCount, true);
        }
        if (aheadCount > 0) {
            return Position.waiting(aheadCount, false);
        }
        if (!userId.equals(turnUserId)) {
            turnUserId = userId;
            turnStartedAt = now;
        }
        return Position.myTurn(turnStartedAt.plus(TURN_TIMEOUT));
    }

    /**
     * 이 회원이 지금 예약할 수 있는 차례인지. 아무도 기다리지 않으면 누구나 예약할 수 있다.
     */
    synchronized boolean isTurnOf(Long userId, LocalDateTime now) {
        lastAccessedAt = now;
        prune(now);
        return lastSeenByUser.isEmpty() || userId.equals(lastSeenByUser.keySet().iterator().next());
    }

    /**
     * @return 아직 기다리는 맨 앞 사람의 번호표, 아무도 없으면 null
     */
    synchronized Long frontTicket(LocalDateTime now) {
        prune(now);
        if (lastSeenByUser.isEmpty()) {
            return null;
        }
        return ticketByUser.get(lastSeenByUser.keySet().iterator().next());
    }

    /**
     * @return 이 회원이 줄에 서 있으면 번호표, 아니면 null
     */
    synchronized Long ticketOf(Long userId, LocalDateTime now) {
        prune(now);
        return ticketByUser.get(userId);
    }

    synchronized void remove(Long userId, LocalDateTime now) {
        lastAccessedAt = now;
        lastSeenByUser.remove(userId);
        ticketByUser.remove(userId);
        if (userId.equals(turnUserId)) {
            turnUserId = null;
        }
        invalidateState();
    }

    /** 기다리는 사람이 없고 한동안 아무도 묻지 않은 줄이면 지워도 된다 */
    synchronized boolean isIdle(LocalDateTime now) {
        prune(now);
        return lastSeenByUser.isEmpty() && lastAccessedAt.plus(HEARTBEAT_TIMEOUT).isBefore(now);
    }

    /** 대기 화면을 닫고 떠난 사람과, 차례가 왔는데 예약하지 않은 맨 앞 사람을 뺀다 */
    private void prune(LocalDateTime now) {
        LocalDateTime staleBefore = now.minus(HEARTBEAT_TIMEOUT);
        Iterator<Map.Entry<Long, LocalDateTime>> entries = lastSeenByUser.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Long, LocalDateTime> entry = entries.next();
            if (entry.getValue().isBefore(staleBefore)) {
                ticketByUser.remove(entry.getKey());
                entries.remove();
            }
        }
        Long front = lastSeenByUser.isEmpty() ? null : lastSeenByUser.keySet().iterator().next();
        if (turnUserId != null && turnUserId.equals(front)
                && turnStartedAt.plus(TURN_TIMEOUT).isBefore(now)) {
            lastSeenByUser.remove(front);
            ticketByUser.remove(front);
            turnUserId = null;
        } else if (turnUserId != null && !turnUserId.equals(front)) {
            turnUserId = null;
        }
    }

    private int indexOf(Long userId) {
        List<Long> order = new ArrayList<>(lastSeenByUser.keySet());
        return order.indexOf(userId);
    }

    /**
     * @param aheadCount    내 앞에서 기다리는 사람 수
     * @param paying        다른 사람이 결제 중인지
     * @param turnExpiresAt 내 차례가 끝나는 시각, 내 차례가 아니면 null
     */
    record Position(int aheadCount, boolean paying, LocalDateTime turnExpiresAt) {

        static Position waiting(int aheadCount, boolean paying) {
            return new Position(aheadCount, paying, null);
        }

        static Position myTurn(LocalDateTime turnExpiresAt) {
            return new Position(0, false, turnExpiresAt);
        }

        boolean isMyTurn() {
            return turnExpiresAt != null;
        }
    }

    /**
     * DB 에서 읽은 빈자리 상태. 같은 줄의 모든 사람이 함께 쓰므로 누가 묻는지와 관계없는 내용만 담는다.
     *
     * @param kind  비었는지, 점유가 막고 있는지, 더 예약할 수 없는지
     * @param holds 이 시간과 겹치는 유효한 점유 (HELD 일 때만)
     */
    record SlotState(Kind kind, List<Hold> holds) {

        enum Kind { FREE, HELD, CLOSED }

        static SlotState free() {
            return new SlotState(Kind.FREE, List.of());
        }

        static SlotState closed() {
            return new SlotState(Kind.CLOSED, List.of());
        }

        static SlotState held(List<Hold> holds) {
            return new SlotState(Kind.HELD, List.copyOf(holds));
        }

        boolean isHeld() {
            return kind == Kind.HELD;
        }
    }

    /**
     * 시간을 막고 있는 점유 하나.
     *
     * @param exact 점유의 시작·끝 시각이 이 줄의 시간과 똑같은지
     */
    record Hold(Long userId, Long reservationId, Long treatmentId, LocalDateTime startsAt, boolean exact) {
    }

    /** 줄을 나누는 기준. 같은 매장에서 시작·끝 일시가 같은 시간을 누른 사람끼리 한 줄에 선다 */
    record SlotKey(Long shopId, LocalDateTime startsAt, LocalDateTime endsAt) {

        boolean overlaps(SlotKey other) {
            return shopId.equals(other.shopId) && SalePolicy.overlaps(startsAt, endsAt, other.startsAt, other.endsAt);
        }
    }
}
