package com.ucamp.app.domain.payment.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.payment.dto.request.PaymentStartRequest;
import com.ucamp.app.domain.payment.dto.response.PaymentStartResponse;
import com.ucamp.app.domain.reservation.service.ReservationService;

/**
 * 결제창을 열기 전에 "이 예약은 지금 이 화면이 결제한다"를 정한다.
 * 같은 계정이 같은 결제 화면을 두 탭에 열어 둘 다 결제를 누르면 카드가 두 번 결제되고 한쪽은 자동 취소된다.
 * 결제창이 뜨기 전에 한 화면만 통과시켜 두 번 결제되는 일 자체를 막는다.
 * <p>
 * 화면은 탭마다 하나인 tabId 를 보낸다. 같은 탭이 다시 누르면(결제창을 닫고 다시 시도) 그대로 통과하고 같은 주문번호를 준다.
 * 결제창을 닫거나 결제가 실패하면 화면이 중단을 알리고, 결제 확인이 끝나면 서버가 푼다.
 * 탭을 그냥 닫아 아무 알림이 없으면 {@link #ATTEMPT_TIMEOUT} 뒤에 다른 탭이 이어받을 수 있다.
 * <p>
 * 대기열처럼 서버 메모리에 둔다 (서버 1대 기준). 재시작하면 비워지지만, 그때도 늦게 온 결제는 확정 단계에서 막히고 자동 취소된다.
 */
@Service
public class PaymentStartService {

    /**
     * 결제를 시작한 화면이 아무 알림 없이 사라졌다고 보는 시간. 카드 인증(앱 인증·추가 인증)까지 끝낼 수 있게 넉넉히 두되,
     * 실수로 탭을 닫은 고객이 점유 시간(5분) 안에 다른 탭에서 다시 결제할 수 있게 그보다 짧게 둔다.
     */
    static final Duration ATTEMPT_TIMEOUT = Duration.ofMinutes(3);

    private final ConcurrentHashMap<Long, Attempt> attempts = new ConcurrentHashMap<>();
    private final ReservationService reservationService;
    private final Clock clock;

    public PaymentStartService(ReservationService reservationService, Clock clock) {
        this.reservationService = reservationService;
        this.clock = clock;
    }

    /**
     * 이 화면이 결제를 시작한다. 결제창에 쓸 주문번호를 돌려준다.
     *
     * @throws BusinessException 고객이 아니면 FORBIDDEN,
     *                           다른 화면이 이 예약을 결제하는 중이면 PAYMENT_IN_PROGRESS,
     *                           지금 결제할 수 없는 예약이면 ReservationService.validatePayable 의 오류
     */
    public PaymentStartResponse start(AuthUser loginUser, PaymentStartRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        Long reservationId = request.reservationId();
        // 결제창을 연 뒤 확정에 실패하면 자동 취소해야 하므로, 열 수 없는 예약은 여기서 먼저 돌려보낸다
        reservationService.validatePayable(loginUser.userId(), reservationId);
        LocalDateTime now = LocalDateTime.now(clock);
        // 판단은 한 예약 단위로 원자적으로 하고, 막힌 경우의 예외는 맵 갱신이 끝난 뒤 밖에서 던진다
        Attempt attempt = attempts.compute(reservationId, (id, current) -> {
            if (current == null || !current.isActive(now)) {
                return new Attempt(loginUser.userId(), request.tabId(), newMerchantUid(id), now);
            }
            // 같은 탭이 다시 시도하면 주문번호는 그대로 두고 시간만 새로 센다. 다른 탭의 결제는 건드리지 않는다
            return current.isOf(loginUser.userId(), request.tabId()) ? current.restartedAt(now) : current;
        });
        if (!attempt.isOf(loginUser.userId(), request.tabId())) {
            throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
        return new PaymentStartResponse(attempt.merchantUid());
    }

    /**
     * 결제창을 닫았거나 결제가 실패해 이 화면이 결제를 그만둔다. 다른 화면이 바로 결제할 수 있다.
     * 내가 시작한 결제가 아니면 아무것도 하지 않아, 다른 탭의 진행 중 결제를 풀지 못한다.
     *
     * @throws BusinessException 고객이 아니면 FORBIDDEN
     */
    public void abort(AuthUser loginUser, PaymentStartRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        attempts.computeIfPresent(request.reservationId(),
                (id, current) -> current.isOf(loginUser.userId(), request.tabId()) ? null : current);
    }

    /**
     * 결제 확인이 끝나면(확정·실패 모두) 진행 중 표시를 푼다. 확정됐으면 다음 시작은 예약 상태 검사에서 막힌다.
     * 예약 주인이 아니면 풀지 않아, 남의 예약 번호로 확인을 보내 진행 중 표시를 지우지 못한다.
     */
    void finish(Long userId, Long reservationId) {
        attempts.computeIfPresent(reservationId, (id, current) -> current.userId().equals(userId) ? null : current);
    }

    /**
     * 탭을 닫아 중단 알림이 오지 않은 결제 시작을 지운다. 메모리에 쌓이지 않도록 주기 작업에서 부른다.
     * 시간이 지난 기록은 지우기 전에도 start 가 새 결제로 덮어쓰므로, 이 정리는 결제 동작에는 영향을 주지 않는다.
     *
     */
    public void sweepTimedOut() {
        LocalDateTime now = LocalDateTime.now(clock);
        // ConcurrentHashMap 의 values().removeIf 는 확인한 값이 그대로일 때만 지운다.
        // 그사이 같은 탭이 다시 시작해 기록이 바뀌었으면 지우지 않는다
        attempts.values().removeIf(attempt -> !attempt.isActive(now));
    }

    // 테스트에서 정리 결과를 확인한다
    int attemptCount() {
        return attempts.size();
    }

    // 결제 확인은 앞부분(reservation-{예약 ID}-)으로 결제와 예약의 짝을 맞춘다 (PaymentService.merchantUidPrefix)
    private static String newMerchantUid(Long reservationId) {
        return PaymentService.merchantUidPrefix(reservationId) + UUID.randomUUID().toString().replace("-", "");
    }

    /** 한 화면이 시작한 결제 */
    private record Attempt(Long userId, String tabId, String merchantUid, LocalDateTime startedAt) {

        boolean isActive(LocalDateTime now) {
            return now.isBefore(startedAt.plus(ATTEMPT_TIMEOUT));
        }

        Attempt restartedAt(LocalDateTime now) {
            return new Attempt(userId, tabId, merchantUid, now);
        }

        boolean isOf(Long otherUserId, String otherTabId) {
            return userId.equals(otherUserId) && tabId.equals(otherTabId);
        }
    }
}
