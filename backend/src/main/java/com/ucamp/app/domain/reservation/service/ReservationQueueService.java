package com.ucamp.app.domain.reservation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.reservation.dto.request.ReservationCreateRequest;
import com.ucamp.app.domain.reservation.dto.request.ReservationQueueRequest;
import com.ucamp.app.domain.reservation.dto.response.ReservationQueueResponse;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.reservation.service.ReservationQueue.Hold;
import com.ucamp.app.domain.reservation.service.ReservationQueue.Position;
import com.ucamp.app.domain.reservation.service.ReservationQueue.SlotKey;
import com.ucamp.app.domain.reservation.service.ReservationQueue.SlotState;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;
import com.ucamp.app.domain.vacancy.policy.SalePolicy;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

/**
 * 예약 대기열. 알림을 받은 여러 고객이 같은 빈자리를 동시에 누르면 누른 순서대로 번호표를 주고 한 명씩 예약하게 한다.
 * 맨 앞 사람이 점유(HOLD)하면 나머지는 기다리고, 점유가 결제 없이 풀리면 다음 사람 차례가 된다.
 * 확정되거나 마감되면 기다리던 사람 모두에게 마감을 알린다.
 * <p>
 * 1인 매장이라 시간이 겹치면 시술이 달라도 함께 예약할 수 없다. 그래서 줄은 매장 + 시작~끝 시각으로 나누고,
 * 다음 규칙으로 기다리는 사람이 옆 시간 예약에 밀려나지 않게 한다.
 * <ul>
 *   <li>점유와 시작·끝 시각이 정확히 같을 때만 기다릴 수 있다. 일부만 겹치면 기다려도 못 잡을 수 있어 예약 불가(CLOSED)다.</li>
 *   <li>겹치는 다른 시간의 줄에 먼저 선 사람이 있으면, 그 사람 차례가 끝날 때까지 이 시간은 예약 불가다.
 *       점유가 풀리는 순간 옆 시간으로 끼어들어 기다린 사람을 밀어내지 못하게 한다.
 *       기다릴 수 있는 사람은 정확히 같은 시간의 점유에 막힌 사람뿐이므로 이 막힘은 한 단계에서 끝난다.</li>
 *   <li>시간을 막고 있는 점유가 내 것이면 줄에 서지 않고 바로 예약 단계로 보낸다. 화면은 "기존 예약을 바꿀까요?"를 묻는다.</li>
 * </ul>
 * 줄은 서버 메모리에 둔다 (서버 1대 기준). 같은 시간 두 명 점유를 막는 것은 여전히 ReservationService 의 잠금이다.
 */
@Service
@Transactional(readOnly = true)
public class ReservationQueueService {

    private final ConcurrentHashMap<SlotKey, ReservationQueue> queues = new ConcurrentHashMap<>();
    private final ReservationRepository reservationRepository;
    private final TreatmentRepository treatmentRepository;
    private final AvailabilityService availabilityService;
    private final Clock clock;
    /**
     * createInTurn 은 트랜잭션 없이 돌아, 그 안에서 바로 조회하면 JPA 가 연결을 메서드가 끝날 때까지 쥐고 있는다.
     * 그 상태로 예약 생성이 연결을 하나 더 요구하면 동시 요청이 많을 때 연결 풀이 바닥나 서로 기다리다 멈춘다.
     * 사전 조회는 이 짧은 읽기 트랜잭션 안에서 끝내 연결을 바로 돌려준다.
     */
    private final TransactionTemplate readTransaction;

    public ReservationQueueService(ReservationRepository reservationRepository,
            TreatmentRepository treatmentRepository, AvailabilityService availabilityService, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.reservationRepository = reservationRepository;
        this.treatmentRepository = treatmentRepository;
        this.availabilityService = availabilityService;
        this.clock = clock;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    /**
     * 빈자리 줄에 서거나, 이미 서 있으면 지금 순서를 돌려준다. 대기 화면은 몇 초마다 이 메서드를 다시 불러
     * 살아 있음을 알린다. 한동안 부르지 않으면 줄에서 빠진다.
     *
     * @param loginUser 로그인한 고객
     * @param request   시술 ID, 시작 일시
     * @return 내 순서. 이 시술·시각을 이미 점유 중이면 HOLDING, 더 예약할 수 없으면 CLOSED.
     *         내 다른 점유가 이 시간을 막고 있으면 줄에 세우지 않고 MY_TURN 을 준다
     * @throws BusinessException 고객이 아니면 FORBIDDEN
     */
    public ReservationQueueResponse joinOrRefresh(AuthUser loginUser, ReservationQueueRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        LocalDateTime now = LocalDateTime.now(clock);
        Slot slot = resolve(request.treatmentId(), request.startsAt());
        if (slot == null) {
            return ReservationQueueResponse.closed(now);
        }
        SlotKey key = slot.key();
        // 없는 시술·마감된 시각처럼 기다려도 예약할 수 없는 빈자리에는 줄을 만들지 않아 메모리에 쌓이지 않게 한다.
        // 이 조회 결과는 줄을 만든 뒤의 상태 변경과 어긋날 수 있어 캐시하지 않는다
        if (!queues.containsKey(key) && view(readState(key, now), loginUser, slot) == View.CLOSED) {
            return ReservationQueueResponse.closed(now);
        }
        ReservationQueue queue = acquire(key, now);
        SlotState state = loadState(queue, key, now);

        switch (view(state, loginUser, slot)) {
            case CLOSED -> {
                queue.remove(loginUser.userId(), now);
                return ReservationQueueResponse.closed(now);
            }
            case HOLDING -> {
                queue.remove(loginUser.userId(), now);
                return ReservationQueueResponse.holding(myExactHold(state, loginUser, slot).reservationId(), now);
            }
            case OWN_HOLD -> {
                // 내 점유 때문에 기다리면 내가 나를 기다리게 된다. 예약 단계에서 바꾸기 여부를 묻도록 바로 보낸다
                queue.remove(loginUser.userId(), now);
                return ReservationQueueResponse.myTurn(now.plus(ReservationQueue.TURN_TIMEOUT), now);
            }
            case WAIT -> {
                Position position = queue.join(loginUser.userId(), true, now);
                return ReservationQueueResponse.waiting(position.aheadCount(), position.paying(), now);
            }
            default -> {
                Position position = queue.join(loginUser.userId(), false, now);
                // 겹치는 다른 시간의 줄에 나보다 먼저 선 사람이 있으면 그 사람 차례를 지켜 준다
                if (hasEarlierOverlappingWaiter(key, queue.ticketOf(loginUser.userId(), now), now)) {
                    queue.remove(loginUser.userId(), now);
                    return ReservationQueueResponse.closed(now);
                }
                if (position.isMyTurn()) {
                    return ReservationQueueResponse.myTurn(position.turnExpiresAt(), now);
                }
                return ReservationQueueResponse.waiting(position.aheadCount(), position.paying(), now);
            }
        }
    }

    /**
     * 줄에서 나간다. 대기 화면을 닫거나 다른 화면으로 갈 때 부른다. 줄에 없어도 성공한다.
     *
     * @param loginUser 로그인한 고객
     * @param request   시술 ID, 시작 일시
     * @throws BusinessException 고객이 아니면 FORBIDDEN
     */
    public void leave(AuthUser loginUser, ReservationQueueRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        Slot slot = resolve(request.treatmentId(), request.startsAt());
        if (slot == null) {
            return;
        }
        ReservationQueue queue = queues.get(slot.key());
        if (queue != null) {
            queue.remove(loginUser.userId(), LocalDateTime.now(clock));
        }
    }

    /**
     * 차례를 확인하고 예약(점유)을 만든 뒤 줄에서 뺀다. 기다리는 사람이 있으면 맨 앞 사람만 예약할 수 있다.
     * 확인한 뒤 예약을 만드는 사이에 차례가 끝나 다음 사람에게 넘어가지 않도록, 줄이 있으면 세 단계를 그 줄의 잠금 안에서 한다.
     * 그동안 같은 빈자리의 순서 조회는 잠시 기다린다. 예약 생성이 자기 트랜잭션을 잠금 안에서 끝내도록
     * 이 메서드는 트랜잭션 없이 실행한다.
     * 이 시간을 막고 있는 점유가 내 것이면 차례를 보지 않고 바로 예약 생성으로 보낸다.
     * 예약 생성이 같은 점유면 그대로 돌려주고, 다르면 바꾸기(replaceReservationId)를 요구한다.
     *
     * @param create 예약을 만드는 작업
     * @return 만든 예약
     * @throws BusinessException 고객이 아니면 FORBIDDEN (예약 생성과 같은 응답이 되도록 차례보다 먼저 본다),
     *                           겹치는 다른 시간의 줄에 먼저 선 사람이 있으면 VACANCY_TIME_TAKEN,
     *                           앞에 기다리는 사람이 있으면 RESERVATION_QUEUE_NOT_YOUR_TURN
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public <T> T createInTurn(AuthUser loginUser, ReservationCreateRequest request, Supplier<T> create) {
        loginUser.requireRole(UserRole.CUSTOMER);
        LocalDateTime now = LocalDateTime.now(clock);
        Precheck precheck = readTransaction.execute(status -> {
            Slot found = resolve(request.treatmentId(), request.startsAt());
            return found == null ? null : new Precheck(found, view(readState(found.key(), now), loginUser, found));
        });
        if (precheck == null) {
            // 없는 시술·판매 중지 시술은 예약 생성이 알맞은 오류를 낸다
            return create.get();
        }
        SlotKey key = precheck.slot().key();
        View view = precheck.view();
        if (view == View.HOLDING || view == View.OWN_HOLD) {
            T created = create.get();
            completeTurn(loginUser, key);
            return created;
        }
        ReservationQueue queue = queues.get(key);
        Long myTicket = queue == null ? null : queue.ticketOf(loginUser.userId(), now);
        if (hasEarlierOverlappingWaiter(key, myTicket, now)) {
            throw new BusinessException(ErrorCode.VACANCY_TIME_TAKEN);
        }
        if (queue == null) {
            // 아무도 기다리지 않으면 줄 없이 예약한다. 같은 시간 두 명 점유는 예약 생성의 잠금이 막는다
            T created = create.get();
            completeTurn(loginUser, key);
            return created;
        }
        synchronized (queue) {
            if (!queue.isTurnOf(loginUser.userId(), LocalDateTime.now(clock))) {
                throw new BusinessException(ErrorCode.RESERVATION_QUEUE_NOT_YOUR_TURN);
            }
            T created = create.get();
            completeTurn(loginUser, key);
            return created;
        }
    }

    /** 점유한 사람은 줄에서 빼고, 기다리는 사람들이 바로 결제 중으로 보게 한다 */
    private void completeTurn(AuthUser loginUser, SlotKey key) {
        ReservationQueue queue = queues.get(key);
        if (queue != null) {
            queue.remove(loginUser.userId(), LocalDateTime.now(clock));
        }
    }

    /**
     * 기다리는 사람이 없고 한동안 아무도 찾지 않은 줄을 지운다. 주기 작업에서 부른다.
     *
     * @return 지운 줄 수
     */
    public int sweepIdleQueues() {
        LocalDateTime now = LocalDateTime.now(clock);
        int before = queues.size();
        // 줄에 서는 쪽(acquire)과 같은 키 단위로 원자적으로 지워, 막 선 사람이 지워진 줄에 남지 않게 한다
        for (SlotKey key : List.copyOf(queues.keySet())) {
            queues.computeIfPresent(key, (k, queue) -> queue.isIdle(now) ? null : queue);
        }
        return before - queues.size();
    }

    /**
     * 같은 매장에서 이 시간과 겹치는 다른 시간의 줄에, 나보다 먼저 선 사람이 아직 기다리고 있는지.
     *
     * @param myTicket 내 번호표. 줄에 서지 않고 바로 예약하는 경우 null 이며, 기다리는 사람이 있으면 항상 뒤로 본다
     */
    private boolean hasEarlierOverlappingWaiter(SlotKey key, Long myTicket, LocalDateTime now) {
        for (Map.Entry<SlotKey, ReservationQueue> entry : queues.entrySet()) {
            if (entry.getKey().equals(key) || !entry.getKey().overlaps(key)) {
                continue;
            }
            Long front = entry.getValue().frontTicket(now);
            if (front != null && (myTicket == null || front < myTicket)) {
                return true;
            }
        }
        return false;
    }

    private ReservationQueue acquire(SlotKey key, LocalDateTime now) {
        return queues.compute(key, (k, queue) -> {
            if (queue == null) {
                return new ReservationQueue(now);
            }
            queue.touch(now);
            return queue;
        });
    }

    /**
     * 빈자리가 비었는지, 누가 점유 중인지, 더 예약할 수 없는지를 읽는다.
     * 같은 시간대의 다른 시술 점유도 이 시간을 막으므로 매장 전체의 겹치는 예약을 본다.
     */
    private SlotState loadState(ReservationQueue queue, SlotKey key, LocalDateTime now) {
        SlotState cached = queue.cachedState(now);
        if (cached != null) {
            return cached;
        }
        SlotState state = readState(key, now);
        queue.cacheState(state, now);
        return state;
    }

    private SlotState readState(SlotKey key, LocalDateTime now) {
        if (!SalePolicy.isBookable(key.startsAt(), now)) {
            return SlotState.closed();
        }
        List<Reservation> occupying = reservationRepository
                .findActiveOverlapping(key.shopId(), key.startsAt(), key.endsAt()).stream()
                .filter(reservation -> SalePolicy.isOccupying(
                        reservation.getStatus(), reservation.getHoldExpiresAt(), now))
                .toList();
        // 확정·취소 처리 중인 예약은 결제 없이 풀리지 않으므로 기다려도 소용없다
        if (occupying.stream().anyMatch(reservation -> reservation.getStatus() != ReservationStatus.HOLD)) {
            return SlotState.closed();
        }
        if (!occupying.isEmpty()) {
            return SlotState.held(occupying.stream()
                    .map(reservation -> new Hold(reservation.getUserId(), reservation.getId(),
                            reservation.getTreatmentId(), reservation.getStartsAt(),
                            reservation.getStartsAt().equals(key.startsAt())
                                    && reservation.getEndsAt().equals(key.endsAt())))
                    .toList());
        }
        int durationMinutes = (int) Duration.between(key.startsAt(), key.endsAt()).toMinutes();
        if (!availabilityService.isAvailable(key.shopId(), key.startsAt(), durationMinutes)) {
            return SlotState.closed();
        }
        return SlotState.free();
    }

    /**
     * 줄 전체가 함께 쓰는 빈자리 상태를, 묻는 사람 입장에서 읽는다.
     */
    private static View view(SlotState state, AuthUser loginUser, Slot slot) {
        if (state.kind() == SlotState.Kind.CLOSED) {
            return View.CLOSED;
        }
        if (!state.isHeld()) {
            return View.FREE;
        }
        List<Hold> holds = state.holds();
        if (holds.stream().allMatch(hold -> hold.userId().equals(loginUser.userId()))) {
            return holds.stream().anyMatch(hold -> isSameSlot(hold, slot)) ? View.HOLDING : View.OWN_HOLD;
        }
        // 다른 사람의 점유 하나가 시간을 정확히 막고 있을 때만, 풀리면 그대로 이 시간이 비므로 기다릴 수 있다
        if (holds.size() == 1 && holds.get(0).exact()) {
            return View.WAIT;
        }
        return View.CLOSED;
    }

    private static Hold myExactHold(SlotState state, AuthUser loginUser, Slot slot) {
        return state.holds().stream()
                .filter(hold -> hold.userId().equals(loginUser.userId()) && isSameSlot(hold, slot))
                .findFirst()
                .orElseThrow();
    }

    private static boolean isSameSlot(Hold hold, Slot slot) {
        return hold.treatmentId().equals(slot.treatmentId()) && hold.startsAt().equals(slot.key().startsAt());
    }

    /**
     * 시술 ID 와 시작 일시로 줄 키를 만든다. 판매 중인 시술이 아니면 null.
     */
    private Slot resolve(Long treatmentId, OffsetDateTime startsAt) {
        Treatment treatment = treatmentRepository.findById(treatmentId)
                .filter(found -> found.getStatus() == TreatmentStatus.ACTIVE)
                .orElse(null);
        if (treatment == null || treatment.getDurationMinutes() <= 0) {
            return null;
        }
        LocalDateTime start = TimeUtils.toLocal(startsAt);
        return new Slot(treatmentId, new SlotKey(treatment.getShop().getShopId(), start,
                start.plusMinutes(treatment.getDurationMinutes())));
    }

    /** 고객이 누른 시술과, 그 시술이 차지하는 줄 */
    private record Slot(Long treatmentId, SlotKey key) {
    }

    /** 예약 생성 전에 읽어 둔 줄과, 묻는 사람 입장의 빈자리 */
    private record Precheck(Slot slot, View view) {
    }

    /** 묻는 사람 입장에서 본 빈자리 */
    private enum View {
        /** 비었다. 줄 순서대로 예약한다 */
        FREE,
        /** 다른 사람의 점유가 시간을 정확히 막고 있다. 풀리기를 기다린다 */
        WAIT,
        /** 이 시술·시각을 내가 이미 점유하고 있다 */
        HOLDING,
        /** 내 다른 점유가 이 시간을 막고 있다. 바꾸기를 묻는다 */
        OWN_HOLD,
        /** 기다려도 예약할 수 없다 */
        CLOSED
    }
}
