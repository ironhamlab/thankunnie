package com.ucamp.app.domain.reservation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    public ReservationQueueService(ReservationRepository reservationRepository,
            TreatmentRepository treatmentRepository, AvailabilityService availabilityService, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.treatmentRepository = treatmentRepository;
        this.availabilityService = availabilityService;
        this.clock = clock;
    }

    /**
     * 빈자리 줄에 서거나, 이미 서 있으면 지금 순서를 돌려준다. 대기 화면은 몇 초마다 이 메서드를 다시 불러
     * 살아 있음을 알린다. 한동안 부르지 않으면 줄에서 빠진다.
     *
     * @param loginUser 로그인한 고객
     * @param request   시술 ID, 시작 일시
     * @return 내 순서. 점유 중이면 HOLDING, 더 예약할 수 없으면 CLOSED
     * @throws BusinessException 고객이 아니면 FORBIDDEN
     */
    public ReservationQueueResponse joinOrRefresh(AuthUser loginUser, ReservationQueueRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        SlotKey key = toKey(request.treatmentId(), request.startsAt());
        LocalDateTime now = LocalDateTime.now(clock);
        // 없는 시술·마감된 시각처럼 기다려도 예약할 수 없는 빈자리에는 줄을 만들지 않아 메모리에 쌓이지 않게 한다.
        // 이 조회 결과는 줄을 만든 뒤의 상태 변경과 어긋날 수 있어 캐시하지 않는다
        if (!queues.containsKey(key) && readState(key, now).kind() == SlotState.Kind.CLOSED) {
            return ReservationQueueResponse.closed(now);
        }
        ReservationQueue queue = acquire(key, now);
        SlotState state = loadState(queue, key, now);

        if (state.kind() == SlotState.Kind.CLOSED) {
            queue.remove(loginUser.userId(), now);
            return ReservationQueueResponse.closed(now);
        }
        if (state.isHeldBy(loginUser.userId())) {
            queue.remove(loginUser.userId(), now);
            return ReservationQueueResponse.holding(state.holdId(), now);
        }
        Position position = queue.join(loginUser.userId(), state, now);
        if (position.isMyTurn()) {
            return ReservationQueueResponse.myTurn(position.turnExpiresAt(), now);
        }
        return ReservationQueueResponse.waiting(position.aheadCount(), position.paying(), now);
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
        ReservationQueue queue = queues.get(toKey(request.treatmentId(), request.startsAt()));
        if (queue != null) {
            queue.remove(loginUser.userId(), LocalDateTime.now(clock));
        }
    }

    /**
     * 차례를 확인하고 예약(점유)을 만든 뒤 줄에서 뺀다. 기다리는 사람이 있으면 맨 앞 사람만 예약할 수 있다.
     * 확인한 뒤 예약을 만드는 사이에 차례가 끝나 다음 사람에게 넘어가지 않도록, 줄이 있으면 세 단계를 그 줄의 잠금 안에서 한다.
     * 그동안 같은 빈자리의 순서 조회는 잠시 기다린다. 예약 생성이 자기 트랜잭션을 잠금 안에서 끝내도록
     * 이 메서드는 트랜잭션 없이 실행한다.
     *
     * @param create 예약을 만드는 작업
     * @return 만든 예약
     * @throws BusinessException 고객이 아니면 FORBIDDEN (예약 생성과 같은 응답이 되도록 차례보다 먼저 본다),
     *                           앞에 기다리는 사람이 있으면 RESERVATION_QUEUE_NOT_YOUR_TURN
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public <T> T createInTurn(AuthUser loginUser, ReservationCreateRequest request, Supplier<T> create) {
        loginUser.requireRole(UserRole.CUSTOMER);
        SlotKey key = toKey(request.treatmentId(), request.startsAt());
        ReservationQueue queue = queues.get(key);
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
        Treatment treatment = treatmentRepository.findById(key.treatmentId())
                .filter(found -> found.getStatus() == TreatmentStatus.ACTIVE)
                .orElse(null);
        if (treatment == null || !SalePolicy.isBookable(key.startsAt(), now)) {
            return SlotState.closed();
        }
        Long shopId = treatment.getShop().getShopId();
        LocalDateTime endsAt = key.startsAt().plusMinutes(treatment.getDurationMinutes());
        List<Reservation> occupying = reservationRepository.findActiveOverlapping(shopId, key.startsAt(), endsAt)
                .stream()
                .filter(reservation -> SalePolicy.isOccupying(
                        reservation.getStatus(), reservation.getHoldExpiresAt(), now))
                .toList();
        // 확정·취소 처리 중인 예약은 결제 없이 풀리지 않으므로 기다려도 소용없다
        if (occupying.stream().anyMatch(reservation -> reservation.getStatus() != ReservationStatus.HOLD)) {
            return SlotState.closed();
        }
        if (!occupying.isEmpty()) {
            Reservation hold = occupying.stream()
                    .filter(reservation -> reservation.isSameSlot(key.treatmentId(), key.startsAt()))
                    .findFirst()
                    .orElse(occupying.get(0));
            return SlotState.held(hold.getUserId(), hold.getId(),
                    hold.isSameSlot(key.treatmentId(), key.startsAt()));
        }
        if (!availabilityService.isAvailable(shopId, key.startsAt(), treatment.getDurationMinutes())) {
            return SlotState.closed();
        }
        return SlotState.free();
    }

    private static SlotKey toKey(Long treatmentId, OffsetDateTime startsAt) {
        return new SlotKey(treatmentId, TimeUtils.toLocal(startsAt));
    }
}
