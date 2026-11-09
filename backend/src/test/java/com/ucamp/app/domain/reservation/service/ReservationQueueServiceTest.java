package com.ucamp.app.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.domain.reservation.dto.request.ReservationCreateRequest;
import com.ucamp.app.domain.reservation.dto.request.ReservationQueueRequest;
import com.ucamp.app.domain.reservation.dto.response.ReservationQueueResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationQueueStatus;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.shop.entity.Shop;
import com.ucamp.app.domain.treatment.dto.request.TreatmentSaveRequest;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationQueueServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 9, 0);
    private static final LocalDateTime STARTS_AT = NOW.plusHours(3);
    private static final Long TREATMENT_ID = 1L;
    private static final Long SHOP_ID = 1L;
    private static final AuthUser FIRST = customer(1L);
    private static final AuthUser SECOND = customer(2L);
    private static final AuthUser THIRD = customer(3L);

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private AvailabilityService availabilityService;

    private MutableClock clock;
    private ReservationQueueService queueService;
    private final List<Reservation> activeReservations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW.atZone(BookingPolicy.ZONE).toInstant());
        queueService = new ReservationQueueService(reservationRepository, treatmentRepository, availabilityService,
                clock, new NoOpTransactionManager());
        given(treatmentRepository.findById(TREATMENT_ID)).willReturn(Optional.of(treatment()));
        given(reservationRepository.findActiveOverlapping(anyLong(), any(), any()))
                .willAnswer(invocation -> List.copyOf(activeReservations));
        given(availabilityService.isAvailable(anyLong(), any(), anyInt())).willReturn(true);
    }

    @Test
    @DisplayName("빈자리에 처음 선 사람은 바로 차례가 되고 뒤에 선 사람은 앞 사람 수를 받는다")
    void join_ordersByArrival() {
        ReservationQueueResponse first = queueService.joinOrRefresh(FIRST, queueRequest());
        ReservationQueueResponse second = queueService.joinOrRefresh(SECOND, queueRequest());
        ReservationQueueResponse third = queueService.joinOrRefresh(THIRD, queueRequest());

        assertThat(first.status()).isEqualTo(ReservationQueueStatus.MY_TURN);
        assertThat(first.turnExpiresAt()).isNotNull();
        assertThat(second.status()).isEqualTo(ReservationQueueStatus.WAITING);
        assertThat(second.aheadCount()).isEqualTo(1);
        assertThat(third.aheadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("다시 순서를 물어도 줄 순서는 바뀌지 않는다")
    void join_again_keepsOrder() {
        queueService.joinOrRefresh(FIRST, queueRequest());
        queueService.joinOrRefresh(SECOND, queueRequest());
        queueService.joinOrRefresh(FIRST, queueRequest());

        assertThat(queueService.joinOrRefresh(SECOND, queueRequest()).aheadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("맨 앞 사람만 예약할 수 있고 뒤에 선 사람은 RESERVATION_QUEUE_NOT_YOUR_TURN 을 받는다")
    void createInTurn_onlyFront() {
        queueService.joinOrRefresh(FIRST, queueRequest());
        queueService.joinOrRefresh(SECOND, queueRequest());

        assertNotYourTurn(SECOND);
        assertThat(queueService.createInTurn(FIRST, createRequest(), () -> 100L)).isEqualTo(100L);
    }

    @Test
    @DisplayName("뒤에 선 사람은 예약 작업을 실행하지 않는다")
    void createInTurn_notYourTurn_skipsCreate() {
        queueService.joinOrRefresh(FIRST, queueRequest());
        queueService.joinOrRefresh(SECOND, queueRequest());
        AtomicBoolean created = new AtomicBoolean();

        assertThatThrownBy(() -> queueService.createInTurn(SECOND, createRequest(), () -> created.getAndSet(true)))
                .isInstanceOf(BusinessException.class);
        assertThat(created).isFalse();
    }

    @Test
    @DisplayName("아무도 기다리지 않는 빈자리는 줄에 서지 않아도 예약할 수 있다")
    void createInTurn_noQueue_allows() {
        assertThatCode(() -> createInTurn(SECOND)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("예약을 만드는 사이 차례 시간이 지나도 다음 사람에게 차례가 넘어가지 않는다")
    void createInTurn_turnExpiresDuringCreate_keepsTurn() throws Exception {
        queueService.joinOrRefresh(FIRST, queueRequest());
        queueService.joinOrRefresh(SECOND, queueRequest());
        AtomicReference<ReservationQueueResponse> secondDuringCreate = new AtomicReference<>();
        Thread second = new Thread(() -> secondDuringCreate.set(queueService.joinOrRefresh(SECOND, queueRequest())));

        queueService.createInTurn(FIRST, createRequest(), () -> {
            // 차례(15초)가 지난 뒤 뒷사람이 순서를 물어도, 예약이 끝날 때까지 기다렸다가 결제 중을 본다
            clock.advance(Duration.ofSeconds(20));
            second.start();
            awaitBlocked(second);
            activeReservations.add(hold(FIRST, 100L, NOW.plusMinutes(5)));
            return null;
        });
        second.join(5_000);

        assertThat(secondDuringCreate.get().status()).isEqualTo(ReservationQueueStatus.WAITING);
        assertThat(secondDuringCreate.get().paying()).isTrue();
    }

    @Test
    @DisplayName("앞 사람이 점유하면 뒷사람은 결제 중으로 기다리고, 점유한 사람은 HOLDING 을 받는다")
    void hold_othersWaitWhilePaying() {
        queueService.joinOrRefresh(FIRST, queueRequest());
        queueService.joinOrRefresh(SECOND, queueRequest());
        queueService.createInTurn(FIRST, createRequest(),
                () -> activeReservations.add(hold(FIRST, 100L, NOW.plusMinutes(5))));

        ReservationQueueResponse second = queueService.joinOrRefresh(SECOND, queueRequest());
        ReservationQueueResponse first = queueService.joinOrRefresh(FIRST, queueRequest());

        assertThat(second.status()).isEqualTo(ReservationQueueStatus.WAITING);
        assertThat(second.paying()).isTrue();
        // 결제 중인 사람도 내 앞에 있는 한 명으로 센다
        assertThat(second.aheadCount()).isEqualTo(1);
        assertThat(first.status()).isEqualTo(ReservationQueueStatus.HOLDING);
        assertThat(first.reservationId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("한 명이 결제 중이고 두 명이 기다리면, 두 번째로 기다리는 사람의 앞 사람 수는 결제 중인 사람을 포함해 2명이다")
    void hold_secondWaiterCountsPayerAhead() {
        queueService.joinOrRefresh(FIRST, queueRequest());
        queueService.joinOrRefresh(SECOND, queueRequest());
        queueService.joinOrRefresh(THIRD, queueRequest());
        queueService.createInTurn(FIRST, createRequest(),
                () -> activeReservations.add(hold(FIRST, 100L, NOW.plusMinutes(5))));

        assertThat(queueService.joinOrRefresh(SECOND, queueRequest()).aheadCount()).isEqualTo(1);
        assertThat(queueService.joinOrRefresh(THIRD, queueRequest()).aheadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("앞 사람 점유가 결제 없이 풀리면 다음 사람 차례가 된다")
    void holdReleased_nextTurn() {
        queueService.joinOrRefresh(FIRST, queueRequest());
        queueService.joinOrRefresh(SECOND, queueRequest());
        queueService.createInTurn(FIRST, createRequest(),
                () -> activeReservations.add(hold(FIRST, 100L, NOW.plusMinutes(5))));
        queueService.joinOrRefresh(SECOND, queueRequest());

        // 5분 점유가 만료되면 겹치는 예약에서 빠진다 (SalePolicy.isOccupying)
        clock.advance(Duration.ofSeconds(10));
        activeReservations.clear();
        queueService.joinOrRefresh(SECOND, queueRequest());
        clock.advance(Duration.ofSeconds(2));

        assertThat(queueService.joinOrRefresh(SECOND, queueRequest()).status())
                .isEqualTo(ReservationQueueStatus.MY_TURN);
        assertThatCode(() -> createInTurn(SECOND)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("예약이 확정되면 기다리던 사람은 CLOSED 를 받는다")
    void confirmed_closesQueue() {
        queueService.joinOrRefresh(SECOND, queueRequest());
        activeReservations.add(confirmed(FIRST));
        clock.advance(Duration.ofSeconds(2));

        assertThat(queueService.joinOrRefresh(SECOND, queueRequest()).status())
                .isEqualTo(ReservationQueueStatus.CLOSED);
    }

    @Test
    @DisplayName("차례가 왔는데 정해진 시간 안에 예약하지 않으면 다음 사람에게 넘어간다")
    void turnTimeout_passesToNext() {
        queueService.joinOrRefresh(FIRST, queueRequest());
        queueService.joinOrRefresh(SECOND, queueRequest());

        // 뒷사람만 계속 순서를 묻고, 맨 앞 사람은 예약도 다시 묻기도 하지 않는다
        for (int i = 0; i < 8; i++) {
            clock.advance(Duration.ofSeconds(2));
            queueService.joinOrRefresh(SECOND, queueRequest());
        }

        assertThat(queueService.joinOrRefresh(SECOND, queueRequest()).status())
                .isEqualTo(ReservationQueueStatus.MY_TURN);
        assertNotYourTurn(FIRST);
    }

    @Test
    @DisplayName("한동안 순서를 묻지 않은 사람은 줄에서 빠진다")
    void heartbeatTimeout_removesWaiter() {
        // 다른 사람이 결제 중이라 아무도 차례가 아니다. 차례 시간 초과와 섞이지 않게 대기 이탈만 본다
        activeReservations.add(hold(customer(9L), 100L, NOW.plusMinutes(5)));
        queueService.joinOrRefresh(FIRST, queueRequest());
        queueService.joinOrRefresh(SECOND, queueRequest());
        queueService.joinOrRefresh(THIRD, queueRequest());

        // 두 번째 사람은 대기 화면을 닫고 떠났다
        for (int i = 0; i < 8; i++) {
            clock.advance(Duration.ofSeconds(2));
            queueService.joinOrRefresh(FIRST, queueRequest());
            queueService.joinOrRefresh(THIRD, queueRequest());
        }

        // 떠난 두 번째 사람은 빠지고, 결제 중인 사람과 첫 번째 사람만 앞에 남는다
        assertThat(queueService.joinOrRefresh(THIRD, queueRequest()).aheadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("줄에서 나가면 뒷사람 순서가 당겨진다")
    void leave_movesOthersForward() {
        queueService.joinOrRefresh(FIRST, queueRequest());
        queueService.joinOrRefresh(SECOND, queueRequest());

        queueService.leave(FIRST, queueRequest());

        assertThat(queueService.joinOrRefresh(SECOND, queueRequest()).status())
                .isEqualTo(ReservationQueueStatus.MY_TURN);
    }

    @Test
    @DisplayName("기다리는 사람이 없는 줄은 정리 작업이 지운다")
    void sweep_removesIdleQueues() {
        queueService.joinOrRefresh(FIRST, queueRequest());
        queueService.leave(FIRST, queueRequest());
        clock.advance(Duration.ofSeconds(30));

        assertThat(queueService.sweepIdleQueues()).isEqualTo(1);
    }

    @Test
    @DisplayName("기다려도 예약할 수 없는 빈자리는 CLOSED 를 주고 줄을 만들지 않는다")
    void join_closedSlot_doesNotCreateQueue() {
        ReservationQueueRequest unknownTreatment = new ReservationQueueRequest(99L, startsAtOffset());

        assertThat(queueService.joinOrRefresh(FIRST, unknownTreatment).status())
                .isEqualTo(ReservationQueueStatus.CLOSED);
        clock.advance(Duration.ofSeconds(30));
        assertThat(queueService.sweepIdleQueues()).isZero();
    }

    @Test
    @DisplayName("매장 계정은 줄에 서거나 나가거나 차례를 확인받을 수 없다")
    void owner_forbidden() {
        AuthUser owner = new AuthUser(9L, UserRole.OWNER, SHOP_ID);
        queueService.joinOrRefresh(FIRST, queueRequest());

        assertForbidden(() -> queueService.joinOrRefresh(owner, queueRequest()));
        assertForbidden(() -> queueService.leave(owner, queueRequest()));
        // 기다리는 사람이 있어도 차례(409)보다 역할(403)을 먼저 알린다
        assertForbidden(() -> createInTurn(owner));
    }

    private Object createInTurn(AuthUser user) {
        return queueService.createInTurn(user, createRequest(), () -> null);
    }

    /** 다른 스레드가 줄의 잠금을 기다리기 시작할 때까지 기다린다 */
    private static void awaitBlocked(Thread thread) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (thread.getState() != Thread.State.BLOCKED) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("줄의 잠금을 기다리지 않았습니다: " + thread.getState());
            }
            Thread.onSpinWait();
        }
    }

    private static void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private void assertNotYourTurn(AuthUser user) {
        assertThatThrownBy(() -> createInTurn(user))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_QUEUE_NOT_YOUR_TURN);
    }

    private static AuthUser customer(Long userId) {
        return new AuthUser(userId, UserRole.CUSTOMER, null);
    }

    private static OffsetDateTime startsAtOffset() {
        return STARTS_AT.atZone(BookingPolicy.ZONE).toOffsetDateTime();
    }

    private static ReservationQueueRequest queueRequest() {
        return new ReservationQueueRequest(TREATMENT_ID, startsAtOffset());
    }

    private static ReservationCreateRequest createRequest() {
        return new ReservationCreateRequest(TREATMENT_ID, startsAtOffset());
    }

    private static Treatment treatment() {
        Shop shop = BeanUtils.instantiateClass(Shop.class);
        ReflectionTestUtils.setField(shop, "shopId", SHOP_ID);
        Treatment treatment = Treatment.create(shop,
                new TreatmentSaveRequest("젤 네일", null, 50_000, 30_000, 18_000, 60));
        ReflectionTestUtils.setField(treatment, "treatmentId", TREATMENT_ID);
        return treatment;
    }

    private static Reservation hold(AuthUser user, Long reservationId, LocalDateTime holdExpiresAt) {
        Reservation reservation = Reservation.builder()
                .userId(user.userId())
                .treatmentId(TREATMENT_ID)
                .startsAt(STARTS_AT)
                .endsAt(STARTS_AT.plusHours(1))
                .appliedPrice(30_000)
                .deposit(20_000)
                .holdExpiresAt(holdExpiresAt)
                .build();
        ReflectionTestUtils.setField(reservation, "id", reservationId);
        return reservation;
    }

    private static Reservation confirmed(AuthUser user) {
        Reservation reservation = hold(user, 101L, NOW.plusMinutes(5));
        reservation.confirm(20_000, NOW, null);
        return reservation;
    }

    /** 저장소를 모의 객체로 바꿨으므로 트랜잭션은 열고 닫는 흉내만 낸다 */
    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    /** 줄의 시간 제한을 확인하려고 테스트 안에서 시각을 앞으로 보낸다 */
    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return BookingPolicy.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
