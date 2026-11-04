package com.ucamp.app.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.ShopLock;
import com.ucamp.app.common.lock.UserLock;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.notification.NotificationFixture;
import com.ucamp.app.domain.reservation.dto.request.ReservationCreateRequest;
import com.ucamp.app.domain.reservation.dto.request.ReservationQueueRequest;
import com.ucamp.app.domain.reservation.dto.response.ReservationCreateResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationQueueResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationQueueStatus;

/**
 * 1인 매장(같은 시간 시술 1건)에서 여러 요청이 동시에 들어올 때 예약 점유(HOLD)가 겹치지 않는지 확인한다.
 * 스레드마다 따로 트랜잭션을 열어야 잠금 경쟁이 일어나므로 테스트에는 @Transactional 을 붙이지 않고,
 * 준비 데이터는 커밋한 뒤 끝나면 직접 지운다.
 * 대기열은 서버 메모리에 남아 테스트끼리 섞일 수 있으므로 테스트마다 다른 시각을 쓴다.
 * 예약 생성은 컨트롤러와 같은 경로(대기열 차례 확인 → 예약 생성)로 부른다.
 */
// 10명이 동시에 예약하면 잠금을 기다리는 요청마다 연결을 하나씩 쥔다. 기본 풀(10개)이면 대기열 사전 조회가
// 연결을 기다리느라 늦게 도착해 "동시에 잠금 앞에 섰다"를 만들 수 없으므로 이 테스트만 풀을 넉넉히 둔다
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=30")
@ActiveProfiles("test")
@Import(ReservationConcurrencyTest.LockTraceConfig.class)
class ReservationConcurrencyTest {

    private static final int PEOPLE = 10;
    private static final long CATEGORY_ID = 9801L;
    private static final long SHOP_ID = 9801L;
    /** 60분 시술 */
    private static final long TREATMENT_A = 9801L;
    /** 같은 매장의 다른 60분 시술 */
    private static final long TREATMENT_B = 9802L;
    private static final long FIRST_USER_ID = 9801L;
    private static final List<String> DAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationQueueService reservationQueueService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    /** 실제 잠금은 그대로 걸고, 언제 요청·획득·해제했는지만 옆에서 기록한다 (LockTraceConfig 가 끼워 넣는다) */
    private static final LockTrace TRACE = new LockTrace();

    private final LockTrace trace = TRACE;

    /**
     * ShopLock·UserLock 은 @Transactional 때문에 이미 스프링 프록시로 감싸여 있다.
     * 그 프록시의 맨 끝(트랜잭션 안쪽)에 기록 단계를 덧붙여, 잠금 동작은 바꾸지 않고 순서만 남긴다.
     */
    @TestConfiguration
    static class LockTraceConfig {

        @Bean
        static BeanPostProcessor lockTracePostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    LockKind kind = bean instanceof ShopLock ? LockKind.SHOP
                            : bean instanceof UserLock ? LockKind.USER : null;
                    if (kind == null) {
                        return bean;
                    }
                    if (!(bean instanceof Advised advised)) {
                        throw new IllegalStateException(beanName + " 가 트랜잭션 프록시가 아니라 잠금 기록을 붙일 수 없다");
                    }
                    advised.addAdvice((MethodInterceptor) invocation -> "lock".equals(invocation.getMethod().getName())
                            ? TRACE.around(kind, (Long) invocation.getArguments()[0], invocation::proceed)
                            : invocation.proceed());
                    return bean;
                }
            };
        }
    }

    @BeforeEach
    void setUpTrace() {
        trace.clear();
    }

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbc, CATEGORY_ID, "동시성 테스트");
        NotificationFixture.shop(jdbc, SHOP_ID, CATEGORY_ID, "11680", "ACTIVE");
        NotificationFixture.treatment(jdbc, TREATMENT_A, SHOP_ID, "NORMAL");
        NotificationFixture.treatment(jdbc, TREATMENT_B, SHOP_ID, "NORMAL");
        // 어느 요일에 돌려도 내일 영업시간 안에 들도록 모든 요일을 연다
        for (int i = 0; i < DAYS.size(); i++) {
            NotificationFixture.businessHour(jdbc, 9801L + i, SHOP_ID, DAYS.get(i), "09:00", "21:00");
        }
        for (int i = 0; i < PEOPLE; i++) {
            NotificationFixture.user(jdbc, FIRST_USER_ID + i);
        }
    }

    @AfterEach
    void tearDown() {
        // 줄은 서버 메모리에 15초 동안 남아, 겹치는 시간을 쓰는 다음 테스트를 막을 수 있으므로 비운다
        reservationQueueService.clearQueues();
        jdbc.update("DELETE FROM RESERVATION WHERE TREATMENT_ID IN (?, ?)", TREATMENT_A, TREATMENT_B);
        jdbc.update("DELETE FROM SHOP_SCHEDULE_EXCEPTION WHERE SHOP_ID = ?", SHOP_ID);
        jdbc.update("DELETE FROM TREATMENT WHERE TREATMENT_ID IN (?, ?)", TREATMENT_A, TREATMENT_B);
        jdbc.update("DELETE FROM SHOP_BUSINESS_HOUR WHERE SHOP_ID = ?", SHOP_ID);
        jdbc.update("DELETE FROM APP_USER WHERE USER_ID BETWEEN ? AND ?", FIRST_USER_ID, FIRST_USER_ID + PEOPLE - 1);
        jdbc.update("DELETE FROM SHOP WHERE SHOP_ID = ?", SHOP_ID);
        jdbc.update("DELETE FROM CATEGORY WHERE CATEGORY_ID = ?", CATEGORY_ID);
    }

    // ---------------------------------------------------------------- 여러 명 동시 예약

    @Test
    @DisplayName("고객 10명이 같은 시술·같은 시간을 동시에 예약하면 한 명만 점유하고 나머지는 이미 예약된 시간으로 막힌다")
    void tenCustomers_sameTreatmentSameTime_onlyOneHolds() throws Exception {
        OffsetDateTime startsAt = tomorrowAt(10, 0);
        List<Callable<ReservationCreateResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < PEOPLE; i++) {
            AuthUser customer = customer(i);
            tasks.add(() -> create(customer, TREATMENT_A, startsAt));
        }

        List<Outcome> outcomes = runAtOnce(tasks);

        // 흐름: 10명 모두 매장 잠금 앞에 동시에 섰고 → 한 명씩 들어갔고 → 가장 먼저 들어간 사람이 점유했다
        assertAllContended(LockKind.SHOP, SHOP_ID, PEOPLE);
        assertSerialized(LockKind.SHOP, SHOP_ID);
        assertUserLockBeforeShopLock();
        assertFirstShopLockHolderWon(outcomes);
        // 나머지는 앞사람이 저장한 점유를 보고 "이미 예약된 시간"에서 막혔다
        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).hasSize(PEOPLE - 1).containsOnly(ErrorCode.VACANCY_TIME_TAKEN);
        assertThat(activeHoldCount()).isEqualTo(1);
        // 저장된 점유 내용이 요청과 시술 소요 시간(60분)대로인지
        ReservationCreateResponse won = outcomes.stream().filter(Outcome::succeeded).findFirst().orElseThrow()
                .response();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM RESERVATION WHERE RESERVATION_ID = ? AND TREATMENT_ID = ? "
                        + "AND STARTS_AT = ? AND ENDS_AT = ? AND HOLD_EXPIRES_AT > ?",
                Integer.class, won.reservationId(), TREATMENT_A, startsAt.toLocalDateTime(),
                startsAt.toLocalDateTime().plusMinutes(60), LocalDateTime.now(clock))).isEqualTo(1);
    }

    @Test
    @DisplayName("고객 10명이 같은 시간의 서로 다른 시술(A·B 반반)을 동시에 예약해도 매장 전체에서 한 명만 점유한다")
    void tenCustomers_mixedTreatmentsSameTime_onlyOneHolds() throws Exception {
        OffsetDateTime startsAt = tomorrowAt(10, 30);
        List<Callable<ReservationCreateResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < PEOPLE; i++) {
            AuthUser customer = customer(i);
            long treatmentId = i % 2 == 0 ? TREATMENT_A : TREATMENT_B;
            tasks.add(() -> create(customer, treatmentId, startsAt));
        }

        List<Outcome> outcomes = runAtOnce(tasks);

        // 시술이 달라도 같은 매장 잠금 하나로 줄을 섰는지 (시술별 잠금이었다면 두 명이 함께 들어간다)
        assertAllContended(LockKind.SHOP, SHOP_ID, PEOPLE);
        assertSerialized(LockKind.SHOP, SHOP_ID);
        assertFirstShopLockHolderWon(outcomes);
        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).hasSize(PEOPLE - 1).containsOnly(ErrorCode.VACANCY_TIME_TAKEN);
        assertThat(activeHoldCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("고객 10명이 30분씩 어긋난 겹치는 시간(11:00~15:30 시작)을 동시에 예약해도 점유끼리 겹치지 않는다")
    void tenCustomers_staggeredOverlappingTimes_noOverlapSaved() throws Exception {
        List<Callable<ReservationCreateResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < PEOPLE; i++) {
            AuthUser customer = customer(i);
            OffsetDateTime startsAt = tomorrowAt(11, 0).plusMinutes(30L * i);
            tasks.add(() -> create(customer, TREATMENT_A, startsAt));
        }

        List<Outcome> outcomes = runAtOnce(tasks);

        assertAllContended(LockKind.SHOP, SHOP_ID, PEOPLE);
        assertSerialized(LockKind.SHOP, SHOP_ID);
        // 처음 들어간 사람은 앞에 아무 예약이 없으므로 반드시 성공해야 한다
        String first = trace.holds(LockKind.SHOP, SHOP_ID).get(0).thread();
        assertThat(outcomes).filteredOn(Outcome::succeeded).extracting(Outcome::thread).contains(first);
        assertThat(failureCodes(outcomes)).containsOnly(ErrorCode.VACANCY_TIME_TAKEN);
        // 60분 시술을 30분 간격으로 10개 넣으면 많아야 한 칸 건너 하나씩, 5건까지 들어갈 수 있다
        assertThat(successCount(outcomes)).isBetween(1L, (long) PEOPLE / 2);
        assertThat(overlappingActivePairs()).as("저장된 점유끼리 겹치면 안 된다").isZero();
    }

    // ---------------------------------------------------------------- 같은 계정

    @Test
    @DisplayName("같은 계정이 같은 시술·같은 시간을 두 번 동시에 누르면 점유 하나만 만들고 둘 다 그 점유를 받는다")
    void sameUser_sameTreatmentSameTime_returnsSameHold() throws Exception {
        OffsetDateTime startsAt = tomorrowAt(12, 0);
        AuthUser me = customer(0);

        List<Outcome> outcomes = runAtOnce(List.of(
                () -> create(me, TREATMENT_A, startsAt),
                () -> create(me, TREATMENT_A, startsAt)));

        // 두 요청이 회원 잠금 앞에서 겹쳤고, 두 번째는 첫 번째가 저장을 끝낸 뒤 들어가 그 점유를 찾았다
        assertAllContended(LockKind.USER, me.userId(), 2);
        assertSerialized(LockKind.USER, me.userId());
        assertThat(successCount(outcomes)).isEqualTo(2);
        assertThat(outcomes.get(0).response().reservationId())
                .isEqualTo(outcomes.get(1).response().reservationId());
        assertThat(activeHoldCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 계정이 같은 시간의 다른 시술 두 개를 동시에 누르면 하나만 점유하고 다른 하나는 결제 대기 예약이 있다고 막힌다")
    void sameUser_differentTreatmentsSameTime_oneHoldOtherHoldExists() throws Exception {
        OffsetDateTime startsAt = tomorrowAt(12, 30);
        AuthUser me = customer(0);

        List<Outcome> outcomes = runAtOnce(List.of(
                () -> create(me, TREATMENT_A, startsAt),
                () -> create(me, TREATMENT_B, startsAt)));

        // 회원 잠금으로 줄 섰고, 먼저 들어간 쪽이 점유하고 뒤쪽은 그 점유를 보고 "결제 대기 예약 있음"으로 막혔다
        assertAllContended(LockKind.USER, me.userId(), 2);
        assertSerialized(LockKind.USER, me.userId());
        assertFirstUserLockHolderWon(outcomes, me.userId());
        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).containsExactly(ErrorCode.RESERVATION_HOLD_EXISTS);
        assertThat(activeHoldCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 계정이 시술 A 를 잡은 뒤 같은 시간의 시술 B 대기열에 서면, 자기 점유 뒤에 줄 서지 않고 바로 바꾸기 단계로 간다")
    void sameUser_holdsA_joinsQueueForB_doesNotWaitBehindOwnHold() {
        OffsetDateTime startsAt = tomorrowAt(13, 0);
        AuthUser me = customer(0);
        ReservationCreateResponse holdA = create(me, TREATMENT_A, startsAt);

        ReservationQueueResponse ticket = reservationQueueService.joinOrRefresh(me,
                new ReservationQueueRequest(TREATMENT_B, startsAt));

        // 자기 점유가 풀리기를 자기가 기다리면 5분 동안 빠져나올 수 없다
        assertThat(ticket.status()).as("내 점유 때문에 대기 중이 되면 안 된다")
                .isNotEqualTo(ReservationQueueStatus.WAITING);
        assertThat(ticket.status()).isEqualTo(ReservationQueueStatus.MY_TURN);

        // 차례가 오면 예약을 만들고, 화면은 결제 대기 예약이 있다는 답을 받아 "바꿀까요?" 창을 띄운다
        assertThatThrownBy(() -> create(me, TREATMENT_B, startsAt))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXISTS);

        // 바꾸기를 누르면 A 가 풀리고 B 가 잡힌다
        ReservationCreateResponse holdB = create(me, TREATMENT_B, startsAt, holdA.reservationId());
        assertThat(statusOf(holdA.reservationId())).isEqualTo("EXPIRED");
        assertThat(statusOf(holdB.reservationId())).isEqualTo("HOLD");
        assertThat(activeHoldCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 계정이 같은 시술의 다른 시간 두 개를 동시에 누르면 하나만 점유하고 다른 하나는 결제 대기 예약이 있다고 막힌다")
    void sameUser_sameTreatmentDifferentTimes_oneHoldOtherHoldExists() throws Exception {
        AuthUser me = customer(0);

        List<Outcome> outcomes = runAtOnce(List.of(
                () -> create(me, TREATMENT_A, tomorrowAt(14, 0)),
                () -> create(me, TREATMENT_A, tomorrowAt(16, 0))));

        assertAllContended(LockKind.USER, me.userId(), 2);
        assertSerialized(LockKind.USER, me.userId());
        assertFirstUserLockHolderWon(outcomes, me.userId());
        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).containsExactly(ErrorCode.RESERVATION_HOLD_EXISTS);
        assertThat(activeHoldCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 계정이 다른 시간으로 바꾸면 기존 점유는 풀리고 새 시간만 점유한다. 풀린 시간은 다른 고객이 바로 잡을 수 있다")
    void sameUser_replaceToDifferentTime_releasesOldSlot() {
        OffsetDateTime first = tomorrowAt(14, 30);
        OffsetDateTime second = tomorrowAt(17, 0);
        AuthUser me = customer(0);
        ReservationCreateResponse oldHold = create(me, TREATMENT_A, first);

        ReservationCreateResponse newHold = create(me, TREATMENT_A, second, oldHold.reservationId());

        assertThat(statusOf(oldHold.reservationId())).isEqualTo("EXPIRED");
        assertThat(statusOf(newHold.reservationId())).isEqualTo("HOLD");
        // 풀린 시간이 계속 막혀 있으면 빈자리가 사라진다
        ReservationCreateResponse other = create(customer(1), TREATMENT_A, first);
        assertThat(statusOf(other.reservationId())).isEqualTo("HOLD");
    }

    @Test
    @DisplayName("같은 계정이 바꾸기 확인창을 보는 사이 다른 탭에서 점유가 바뀌었으면, 옛 점유 ID 로 바꾸기를 해도 다시 묻는다")
    void sameUser_replaceWithStaleHoldId_asksAgain() {
        AuthUser me = customer(0);
        ReservationCreateResponse first = create(me, TREATMENT_A, tomorrowAt(15, 0));
        // 다른 탭에서 먼저 바꿔 버렸다
        ReservationCreateResponse second = create(me, TREATMENT_A, tomorrowAt(18, 0), first.reservationId());

        assertThatThrownBy(() -> create(me, TREATMENT_B, tomorrowAt(19, 0), first.reservationId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXISTS);
        assertThat(statusOf(second.reservationId())).isEqualTo("HOLD");
        assertThat(activeHoldCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 다른 계정

    @Test
    @DisplayName("다른 계정 두 명이 같은 시간의 다른 시술을 동시에 예약하면 한 명만 점유한다")
    void differentUsers_differentTreatmentsSameTime_onlyOneHolds() throws Exception {
        OffsetDateTime startsAt = tomorrowAt(15, 30);
        AuthUser first = customer(0);
        AuthUser second = customer(1);

        List<Outcome> outcomes = runAtOnce(List.of(
                () -> create(first, TREATMENT_A, startsAt),
                () -> create(second, TREATMENT_B, startsAt)));

        assertAllContended(LockKind.SHOP, SHOP_ID, 2);
        assertSerialized(LockKind.SHOP, SHOP_ID);
        assertFirstShopLockHolderWon(outcomes);
        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).containsExactly(ErrorCode.VACANCY_TIME_TAKEN);
        assertThat(activeHoldCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 계정 두 명이 같은 시술의 겹치는 시간(16:00~17:00, 16:30~17:30)을 동시에 예약하면 한 명만 점유한다")
    void differentUsers_sameTreatmentOverlappingTimes_onlyOneHolds() throws Exception {
        AuthUser first = customer(0);
        AuthUser second = customer(1);

        List<Outcome> outcomes = runAtOnce(List.of(
                () -> create(first, TREATMENT_A, tomorrowAt(16, 0)),
                () -> create(second, TREATMENT_A, tomorrowAt(16, 30))));

        assertAllContended(LockKind.SHOP, SHOP_ID, 2);
        assertSerialized(LockKind.SHOP, SHOP_ID);
        assertFirstShopLockHolderWon(outcomes);
        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).containsExactly(ErrorCode.VACANCY_TIME_TAKEN);
        assertThat(overlappingActivePairs()).isZero();
    }

    @Test
    @DisplayName("끝나는 시각과 시작 시각이 딱 맞닿는 두 예약(09:00~10:00, 10:00~11:00)은 겹침이 아니라 둘 다 점유한다")
    void differentUsers_adjacentTimes_bothHold() throws Exception {
        AuthUser first = customer(0);
        AuthUser second = customer(1);

        List<Outcome> outcomes = runAtOnce(List.of(
                () -> create(first, TREATMENT_A, tomorrowAt(9, 0)),
                () -> create(second, TREATMENT_B, tomorrowAt(10, 0))));

        // 겹치지 않아도 같은 매장이면 잠금은 한 명씩 지나간다. 뒤에 들어간 쪽이 앞 점유를 보고도 겹침이 아니라 판단했다
        assertAllContended(LockKind.SHOP, SHOP_ID, 2);
        assertSerialized(LockKind.SHOP, SHOP_ID);
        assertThat(successCount(outcomes)).isEqualTo(2);
        assertThat(activeHoldCount()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- 대기 규칙 (정확히 같은 시간만 대기)

    @Test
    @DisplayName("점유와 시간이 정확히 같은 다른 시술을 기다리는 중에 겹치지 않는 시간 예약이 들어와도 순서는 그대로고, 점유가 풀리면 내 차례가 된다")
    void exactWait_otherNonOverlappingBooking_keepsQueueAndTurnComes() {
        AuthUser holder = customer(0);
        AuthUser waiter = customer(1);
        AuthUser outsider = customer(2);
        // holder 가 A 17:30~18:30 을 잡고, waiter 는 같은 시간의 B(17:30~18:30)를 기다린다
        OffsetDateTime startsAt = tomorrowAt(17, 30);
        ReservationCreateResponse holderHold = create(holder, TREATMENT_A, startsAt);
        ReservationQueueResponse waiting = join(waiter, TREATMENT_B, startsAt);
        assertThat(waiting.status()).isEqualTo(ReservationQueueStatus.WAITING);
        assertThat(waiting.paying()).isTrue();

        // 대기 중에 겹치지 않는 20:00 에 다른 고객이 예약한다. 기다리는 시간과 안 겹치니 바로 잡힌다
        ReservationCreateResponse outsiderHold = create(outsider, TREATMENT_B, tomorrowAt(20, 0));
        assertThat(statusOf(outsiderHold.reservationId())).isEqualTo("HOLD");
        assertThat(join(waiter, TREATMENT_B, startsAt).status()).isEqualTo(ReservationQueueStatus.WAITING);

        // 앞 점유가 결제 없이 풀리면 waiter 차례가 오고 예약할 수 있다
        reservationService.releaseHold(holder, holderHold.reservationId());
        assertThat(awaitStatus(waiter, TREATMENT_B, startsAt, ReservationQueueStatus.MY_TURN))
                .isEqualTo(ReservationQueueStatus.MY_TURN);
        ReservationCreateResponse waiterHold = create(waiter, TREATMENT_B, startsAt);
        assertThat(statusOf(waiterHold.reservationId())).isEqualTo("HOLD");
        assertThat(activeHoldCount()).isEqualTo(2);
        assertThat(overlappingActivePairs()).isZero();
    }

    @Test
    @DisplayName("점유와 일부만 겹치는 시간(a 10:00~11:00, b 10:30~11:30)은 대기할 수 없고 예약 불가다")
    void partialOverlap_cannotWait() {
        AuthUser a = customer(0);
        AuthUser b = customer(1);
        create(a, TREATMENT_A, dayAfterAt(10, 0));

        // 기다리게 하면 옆 시간(11:00~)이 먼저 잡혀 a 가 풀려도 b 가 못 잡을 수 있으므로 처음부터 막는다
        assertThat(join(b, TREATMENT_A, dayAfterAt(10, 30)).status()).isEqualTo(ReservationQueueStatus.CLOSED);
        assertThatThrownBy(() -> create(b, TREATMENT_A, dayAfterAt(10, 30)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VACANCY_TIME_TAKEN);
        // 정확히 같은 시간은 다른 시술이어도 기다릴 수 있다
        assertThat(join(b, TREATMENT_B, dayAfterAt(10, 0)).status()).isEqualTo(ReservationQueueStatus.WAITING);
    }

    @Test
    @DisplayName("점유가 풀리는 순간 기다린 사람보다 먼저 겹치는 옆 시간을 누르면 막히고, 기다린 사람이 먼저 잡는다")
    void releaseMoment_cutInToOverlappingTime_blocked() {
        AuthUser a = customer(0);
        AuthUser b = customer(1);
        AuthUser c = customer(2);
        ReservationCreateResponse aHold = create(a, TREATMENT_A, dayAfterAt(12, 0));
        assertThat(join(b, TREATMENT_B, dayAfterAt(12, 0)).status()).isEqualTo(ReservationQueueStatus.WAITING);

        reservationService.releaseHold(a, aHold.reservationId());

        // a 가 풀려 12:30~13:30 이 비어 보여도, 겹치는 12:00 줄에 b 가 먼저 서 있으므로 c 는 끼어들지 못한다
        assertThat(join(c, TREATMENT_A, dayAfterAt(12, 30)).status()).isEqualTo(ReservationQueueStatus.CLOSED);
        int userLocksBefore = trace.requestCount(LockKind.USER);
        assertThatThrownBy(() -> create(c, TREATMENT_A, dayAfterAt(12, 30)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VACANCY_TIME_TAKEN);
        // 흐름: c 는 대기열 단계에서 돌려보내져 예약 생성(잠금)까지 가지 않았다
        assertThat(trace.requestCount(LockKind.USER)).isEqualTo(userLocksBefore);

        assertThat(awaitStatus(b, TREATMENT_B, dayAfterAt(12, 0), ReservationQueueStatus.MY_TURN))
                .isEqualTo(ReservationQueueStatus.MY_TURN);
        ReservationCreateResponse bHold = create(b, TREATMENT_B, dayAfterAt(12, 0));
        assertThat(statusOf(bHold.reservationId())).isEqualTo("HOLD");
        // b 가 잡은 뒤에는 겹치므로 c 는 여전히 예약 불가다
        assertThat(join(c, TREATMENT_A, dayAfterAt(12, 30)).status()).isEqualTo(ReservationQueueStatus.CLOSED);
        assertThat(overlappingActivePairs()).isZero();
    }

    @Test
    @DisplayName("a 가 점유 만료 직후 같은 시간이나 겹치는 시간을 다시 잡으려 해도 기다린 b 보다 앞설 수 없다 (번갈아 잡기 방지)")
    void pingPong_reholdAfterExpiry_cannotJumpWaiter() throws InterruptedException {
        AuthUser a = customer(0);
        AuthUser b = customer(1);
        AuthUser c = customer(2);
        OffsetDateTime startsAt = dayAfterAt(13, 0);
        ReservationCreateResponse aHold = create(a, TREATMENT_A, startsAt);
        assertThat(join(b, TREATMENT_B, startsAt).status()).isEqualTo(ReservationQueueStatus.WAITING);

        // a 의 점유가 결제 없이 만료됐다 (주기 정리 전이라 상태는 HOLD 그대로)
        jdbc.update("UPDATE RESERVATION SET HOLD_EXPIRES_AT = ? WHERE RESERVATION_ID = ?",
                LocalDateTime.now(clock).minusSeconds(1), aHold.reservationId());
        sleepPastStateCache();

        // 같은 시간(다른 시술이어도 같은 줄)을 다시 누르면 b 뒤에 선다
        ReservationQueueResponse aAgain = join(a, TREATMENT_A, startsAt);
        assertThat(aAgain.status()).isEqualTo(ReservationQueueStatus.WAITING);
        assertThat(aAgain.aheadCount()).isEqualTo(1);
        assertThatThrownBy(() -> create(a, TREATMENT_A, startsAt))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_QUEUE_NOT_YOUR_TURN);
        // 겹치는 옆 시간으로 돌아가도 막힌다
        assertThat(join(a, TREATMENT_A, dayAfterAt(13, 30)).status()).isEqualTo(ReservationQueueStatus.CLOSED);
        assertThatThrownBy(() -> create(a, TREATMENT_A, dayAfterAt(13, 30)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VACANCY_TIME_TAKEN);
        // b 와 안 겹치는 14:00~15:00 은 c 가 바로 잡을 수 있다 (막힘은 겹치는 시간에만 걸린다)
        ReservationCreateResponse cHold = create(c, TREATMENT_A, dayAfterAt(14, 0));
        assertThat(statusOf(cHold.reservationId())).isEqualTo("HOLD");

        assertThat(join(b, TREATMENT_B, startsAt).status()).isEqualTo(ReservationQueueStatus.MY_TURN);
        ReservationCreateResponse bHold = create(b, TREATMENT_B, startsAt);
        assertThat(statusOf(bHold.reservationId())).isEqualTo("HOLD");
        assertThat(overlappingActivePairs()).isZero();
    }

    @Test
    @DisplayName("대기자 때문에 막힌 사람은 다른 사람을 막지 않아 막힘이 줄줄이 이어지지 않는다")
    void waiterBlocking_doesNotChain() {
        AuthUser a = customer(0);
        AuthUser b = customer(1);
        AuthUser c = customer(2);
        AuthUser d = customer(3);
        AuthUser e = customer(4);
        // a 15:00~16:00 점유, b 는 같은 시간을 기다린다
        create(a, TREATMENT_A, dayAfterAt(15, 0));
        assertThat(join(b, TREATMENT_B, dayAfterAt(15, 0)).status()).isEqualTo(ReservationQueueStatus.WAITING);
        // c 의 15:30~16:30 은 예약 불가 (c 는 줄에 서지 않는다)
        assertThat(join(c, TREATMENT_A, dayAfterAt(15, 30)).status()).isEqualTo(ReservationQueueStatus.CLOSED);

        // d 의 16:00~17:00 은 c 가 원한 시간과 겹치지만, c 는 기다리는 사람이 아니므로 바로 잡힌다
        ReservationCreateResponse dHold = create(d, TREATMENT_A, dayAfterAt(16, 0));
        assertThat(statusOf(dHold.reservationId())).isEqualTo("HOLD");
        // e 의 17:00~18:00 도 마찬가지다
        ReservationCreateResponse eHold = create(e, TREATMENT_B, dayAfterAt(17, 0));
        assertThat(statusOf(eHold.reservationId())).isEqualTo("HOLD");
        assertThat(overlappingActivePairs()).isZero();
    }

    @Test
    @DisplayName("빈 시간에서 겹치는 두 시간(18:00, 18:30)에 동시에 줄을 서고 동시에 예약해도 한 명만 점유하고, 둘 다 막히지는 않는다")
    void simultaneousOverlappingFreeSlots_exactlyOneHolds() throws Exception {
        AuthUser first = customer(0);
        AuthUser second = customer(1);
        OffsetDateTime firstStartsAt = dayAfterAt(18, 0);
        OffsetDateTime secondStartsAt = dayAfterAt(18, 30);

        runAtOnce(List.of(
                () -> join(first, TREATMENT_A, firstStartsAt),
                () -> join(second, TREATMENT_B, secondStartsAt)));
        List<Outcome> outcomes = runAtOnce(List.of(
                () -> create(first, TREATMENT_A, firstStartsAt),
                () -> create(second, TREATMENT_B, secondStartsAt)));

        // 먼저 줄에 선 쪽이 이기고, 뒤에 선 쪽은 겹치는 대기자 또는 점유를 보고 막힌다. 서로 양보하다 둘 다 실패하지 않는다
        assertThat(successCount(outcomes)).isEqualTo(1);
        assertThat(failureCodes(outcomes)).containsExactly(ErrorCode.VACANCY_TIME_TAKEN);
        assertThat(activeHoldCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("내 점유와 일부만 겹치는 다른 시간을 누르면 기다리지 않고 바로 바꾸기 단계로 간다")
    void sameUser_partialOverlapWithOwnHold_goesToReplace() {
        AuthUser me = customer(0);
        ReservationCreateResponse old = create(me, TREATMENT_A, dayAfterAt(19, 0));

        assertThat(join(me, TREATMENT_B, dayAfterAt(19, 30)).status()).isEqualTo(ReservationQueueStatus.MY_TURN);
        assertThatThrownBy(() -> create(me, TREATMENT_B, dayAfterAt(19, 30)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXISTS);

        ReservationCreateResponse replaced = create(me, TREATMENT_B, dayAfterAt(19, 30), old.reservationId());
        assertThat(statusOf(old.reservationId())).isEqualTo("EXPIRED");
        assertThat(statusOf(replaced.reservationId())).isEqualTo("HOLD");
    }

    // ---------------------------------------------------------------- 대기열

    @Test
    @DisplayName("빈 시간에 10명이 동시에 줄을 서면 한 명만 내 차례고 나머지는 앞사람 수가 1~9로 겹치지 않는다")
    void tenCustomers_joinQueueAtOnce_oneTurnOthersOrdered() throws Exception {
        OffsetDateTime startsAt = tomorrowAt(19, 30);
        List<Callable<ReservationQueueResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < PEOPLE; i++) {
            AuthUser customer = customer(i);
            tasks.add(() -> join(customer, TREATMENT_A, startsAt));
        }

        List<ReservationQueueResponse> tickets = runAtOnceStrict(tasks);

        assertThat(tickets).filteredOn(t -> t.status() == ReservationQueueStatus.MY_TURN).hasSize(1);
        assertThat(tickets).filteredOn(t -> t.status() == ReservationQueueStatus.WAITING)
                .extracting(ReservationQueueResponse::aheadCount)
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    @DisplayName("줄이 있을 때 차례가 아닌 고객이 바로 예약하면 아직 순서가 아니라고 막고, 차례인 고객만 점유한다")
    void queue_notYourTurn_blocked() {
        OffsetDateTime startsAt = tomorrowAt(20, 0).plusDays(1);
        AuthUser front = customer(0);
        AuthUser back = customer(1);
        assertThat(join(front, TREATMENT_A, startsAt).status()).isEqualTo(ReservationQueueStatus.MY_TURN);
        assertThat(join(back, TREATMENT_A, startsAt).status()).isEqualTo(ReservationQueueStatus.WAITING);
        int userLocksBefore = trace.requestCount(LockKind.USER);

        assertThatThrownBy(() -> create(back, TREATMENT_A, startsAt))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_QUEUE_NOT_YOUR_TURN);
        // 차례가 아니면 예약 생성(잠금)까지 가지도 않고 대기열에서 돌려보냈다
        assertThat(trace.requestCount(LockKind.USER)).isEqualTo(userLocksBefore);
        ReservationCreateResponse hold = create(front, TREATMENT_A, startsAt);
        assertThat(statusOf(hold.reservationId())).isEqualTo("HOLD");
        assertThat(join(back, TREATMENT_A, startsAt).paying()).isTrue();
    }

    // ---------------------------------------------------------------- 점유 만료·해제

    @Test
    @DisplayName("만료 시각이 지난 점유는 아직 정리 전이어도 시간을 막지 않아 다른 고객이 바로 잡을 수 있다")
    void expiredHold_doesNotBlock() {
        OffsetDateTime startsAt = tomorrowAt(11, 30).plusDays(1);
        ReservationCreateResponse old = create(customer(0), TREATMENT_A, startsAt);
        // 주기 작업이 꺼져 있으므로 상태는 HOLD 그대로 두고 만료 시각만 지난 것으로 만든다
        jdbc.update("UPDATE RESERVATION SET HOLD_EXPIRES_AT = ? WHERE RESERVATION_ID = ?",
                LocalDateTime.now(clock).minusMinutes(1), old.reservationId());

        ReservationCreateResponse next = create(customer(1), TREATMENT_B, startsAt);

        assertThat(statusOf(next.reservationId())).isEqualTo("HOLD");
    }

    @Test
    @DisplayName("한 고객이 점유를 푸는 순간 다른 고객 10명이 같은 시간을 눌러도 점유는 많아야 하나다")
    void releaseAndTenCreatesAtOnce_atMostOneHold() throws Exception {
        OffsetDateTime startsAt = tomorrowAt(12, 30).plusDays(1);
        AuthUser holder = customer(0);
        ReservationCreateResponse hold = create(holder, TREATMENT_A, startsAt);
        List<Callable<Object>> tasks = new ArrayList<>();
        tasks.add(() -> {
            reservationService.releaseHold(holder, hold.reservationId());
            return null;
        });
        for (int i = 1; i < PEOPLE; i++) {
            AuthUser customer = customer(i);
            long treatmentId = i % 2 == 0 ? TREATMENT_A : TREATMENT_B;
            tasks.add(() -> reservationService.createReservation(customer,
                    new ReservationCreateRequest(treatmentId, startsAt)));
        }

        List<Outcome> outcomes = runAtOnce(tasks);

        assertThat(failureCodes(outcomes)).containsOnly(ErrorCode.VACANCY_TIME_TAKEN);
        assertThat(activeHoldCount()).isLessThanOrEqualTo(1);
        assertThat(overlappingActivePairs()).isZero();
    }

    // ---------------------------------------------------------------- 도우미

    private AuthUser customer(int index) {
        return new AuthUser(FIRST_USER_ID + index, UserRole.CUSTOMER, null);
    }

    /** 앱 시계(Clock)를 그대로 쓰므로 마감(20분 전)·60일 범위에 걸리지 않는 내일로 잡는다 */
    private OffsetDateTime tomorrowAt(int hour, int minute) {
        return LocalDate.now(clock).plusDays(1).atTime(LocalTime.of(hour, minute))
                .atZone(TimeUtils.KST).toOffsetDateTime();
    }

    /** 대기 규칙 테스트는 다른 테스트와 시간이 섞이지 않도록 모레를 쓴다 */
    private OffsetDateTime dayAfterAt(int hour, int minute) {
        return tomorrowAt(hour, minute).plusDays(1);
    }

    /** 대기열은 빈자리 상태를 1초 동안 캐시하므로, DB 를 직접 바꾼 뒤에는 캐시가 지나기를 기다린다 */
    private static void sleepPastStateCache() throws InterruptedException {
        Thread.sleep(1_100);
    }

    private ReservationCreateResponse create(AuthUser user, long treatmentId, OffsetDateTime startsAt) {
        return create(user, treatmentId, startsAt, null);
    }

    /** 컨트롤러와 같이 대기열 차례를 확인한 뒤 예약을 만든다 */
    private ReservationCreateResponse create(AuthUser user, long treatmentId, OffsetDateTime startsAt,
            Long replaceReservationId) {
        ReservationCreateRequest request = new ReservationCreateRequest(treatmentId, startsAt, replaceReservationId);
        return reservationQueueService.createInTurn(user, request,
                () -> reservationService.createReservation(user, request));
    }

    private ReservationQueueResponse join(AuthUser user, long treatmentId, OffsetDateTime startsAt) {
        return reservationQueueService.joinOrRefresh(user, new ReservationQueueRequest(treatmentId, startsAt));
    }

    /** 대기열은 빈자리 상태를 1초 동안 캐시하므로, 원하는 상태가 될 때까지 대기 화면처럼 몇 번 다시 묻는다 */
    private ReservationQueueStatus awaitStatus(AuthUser user, long treatmentId, OffsetDateTime startsAt,
            ReservationQueueStatus expected) {
        ReservationQueueStatus status = null;
        for (int i = 0; i < 20; i++) {
            status = join(user, treatmentId, startsAt).status();
            if (status == expected) {
                return status;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return status;
    }

    private String statusOf(Long reservationId) {
        return jdbc.queryForObject("SELECT STATUS FROM RESERVATION WHERE RESERVATION_ID = ?",
                String.class, reservationId);
    }

    /** 만료 전인 점유와 확정 예약 수 */
    private int activeHoldCount() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM RESERVATION
                WHERE TREATMENT_ID IN (?, ?)
                  AND (STATUS IN ('CONFIRMED', 'CANCEL_PENDING')
                       OR (STATUS = 'HOLD' AND HOLD_EXPIRES_AT > ?))
                """, Integer.class, TREATMENT_A, TREATMENT_B, LocalDateTime.now(clock));
        return count == null ? 0 : count;
    }

    /** 매장에서 시간이 겹치는 유효 예약 쌍의 수. 1인 매장이라 항상 0 이어야 한다 */
    private int overlappingActivePairs() {
        // 만료 시각이 지난 점유는 정리 전이라 HOLD 로 남아 있어도 시간을 막지 않으므로 세지 않는다
        LocalDateTime now = LocalDateTime.now(clock);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM RESERVATION a
                JOIN RESERVATION b ON a.RESERVATION_ID < b.RESERVATION_ID
                 AND a.STARTS_AT < b.ENDS_AT AND a.ENDS_AT > b.STARTS_AT
                WHERE a.TREATMENT_ID IN (?, ?) AND b.TREATMENT_ID IN (?, ?)
                  AND (a.STATUS IN ('CONFIRMED', 'CANCEL_PENDING') OR (a.STATUS = 'HOLD' AND a.HOLD_EXPIRES_AT > ?))
                  AND (b.STATUS IN ('CONFIRMED', 'CANCEL_PENDING') OR (b.STATUS = 'HOLD' AND b.HOLD_EXPIRES_AT > ?))
                """, Integer.class, TREATMENT_A, TREATMENT_B, TREATMENT_A, TREATMENT_B, now, now);
        return count == null ? 0 : count;
    }

    /** @param thread 요청을 처리한 스레드 이름. 잠금 기록(LockTrace)과 결과를 이어 보는 데 쓴다 */
    private record Outcome(String thread, Object value, ErrorCode errorCode) {

        ReservationCreateResponse response() {
            return (ReservationCreateResponse) value;
        }

        boolean succeeded() {
            return errorCode == null;
        }
    }

    private static long successCount(List<Outcome> outcomes) {
        return outcomes.stream().filter(Outcome::succeeded).count();
    }

    private static List<ErrorCode> failureCodes(List<Outcome> outcomes) {
        return outcomes.stream().filter(o -> !o.succeeded()).map(Outcome::errorCode).toList();
    }

    /**
     * 작업들을 최대한 같은 순간에 시작한다. 업무 오류는 결과로 모으고, 그 밖의 오류(잠금 대기 시간 초과 등)는 그대로 던진다.
     */
    private <T> List<Outcome> runAtOnce(List<? extends Callable<? extends T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<? extends T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    String thread = Thread.currentThread().getName();
                    try {
                        return new Outcome(thread, task.call(), null);
                    } catch (BusinessException e) {
                        return new Outcome(thread, null, e.getErrorCode());
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    /** runAtOnce 와 같되 업무 오류도 실패로 본다 */
    private <T> List<T> runAtOnceStrict(List<Callable<T>> tasks) throws Exception {
        List<T> results = new ArrayList<>();
        for (Outcome outcome : runAtOnce(tasks)) {
            assertThat(outcome.errorCode()).as("업무 오류").isNull();
            @SuppressWarnings("unchecked")
            T value = (T) outcome.value();
            results.add(value);
        }
        return results;
    }

    // ---------------------------------------------------------------- 잠금 흐름 기록

    private enum LockKind { USER, SHOP }

    private enum Step { REQUESTED, ACQUIRED, RELEASED }

    private record Event(long seq, String thread, LockKind kind, long key, Step step) {
    }

    /**
     * 한 요청이 잠금 하나를 잡았다 놓은 기록. seq 는 모든 스레드가 함께 쓰는 순번이라 서로 앞뒤를 비교할 수 있다.
     */
    private record Hold(String thread, LockKind kind, long key, long requested, long acquired, long released) {
    }

    @FunctionalInterface
    private interface RealCall {
        Object call() throws Throwable;
    }

    /**
     * 잠금을 요청한 때, 실제로 잡은 때, 놓은 때를 순서대로 남긴다.
     * 놓는 시점은 트랜잭션이 커밋·롤백되기 바로 전(beforeCompletion)에 남긴다. DB 잠금은 그 뒤에 풀리므로
     * 기다리던 요청이 "잡았다"를 남기는 것은 반드시 앞 요청의 "놓았다" 뒤가 된다.
     * 매장 잠금은 잡은 뒤 잠시 쥐고 있어, 동시에 보낸 요청들이 실제로 잠금 앞에서 기다리게 만든다.
     */
    private static final class LockTrace {

        private static final long SHOP_HOLD_MILLIS = 30;

        private final AtomicLong sequence = new AtomicLong();
        private final List<Event> events = java.util.Collections.synchronizedList(new ArrayList<>());

        void clear() {
            events.clear();
        }

        Object around(LockKind kind, Long key, RealCall realCall) throws Throwable {
            String thread = Thread.currentThread().getName();
            record(thread, kind, key, Step.REQUESTED);
            Object result = realCall.call();
            record(thread, kind, key, Step.ACQUIRED);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCompletion() {
                    record(thread, kind, key, Step.RELEASED);
                }
            });
            if (kind == LockKind.SHOP) {
                Thread.sleep(SHOP_HOLD_MILLIS);
            }
            return result;
        }

        private void record(String thread, LockKind kind, long key, Step step) {
            events.add(new Event(sequence.incrementAndGet(), thread, kind, key, step));
        }

        /** 잠금 종류·대상별로 요청 → 획득 → 해제를 짝지어 획득 순서대로 돌려준다 */
        List<Hold> holds(LockKind kind, long key) {
            List<Event> snapshot;
            synchronized (events) {
                snapshot = List.copyOf(events);
            }
            List<Hold> holds = new ArrayList<>();
            java.util.Map<String, long[]> open = new java.util.HashMap<>();
            for (Event event : snapshot) {
                if (event.kind() != kind || event.key() != key) {
                    continue;
                }
                switch (event.step()) {
                    case REQUESTED -> open.put(event.thread(), new long[] {event.seq(), -1});
                    case ACQUIRED -> open.get(event.thread())[1] = event.seq();
                    case RELEASED -> {
                        long[] started = open.remove(event.thread());
                        holds.add(new Hold(event.thread(), kind, key, started[0], started[1], event.seq()));
                    }
                }
            }
            assertThat(open).as("%s 잠금을 잡고 놓지 않은 요청", kind).isEmpty();
            holds.sort(java.util.Comparator.comparingLong(Hold::acquired));
            return holds;
        }

        int requestCount(LockKind kind) {
            synchronized (events) {
                return (int) events.stream()
                        .filter(e -> e.kind() == kind && e.step() == Step.REQUESTED)
                        .count();
            }
        }
    }

    /**
     * 잠금이 정말 한 번에 한 요청만 들여보냈는지 확인한다. 다음 요청은 앞 요청이 놓은 뒤에야 잡아야 한다.
     */
    private void assertSerialized(LockKind kind, long key) {
        List<Hold> holds = trace.holds(kind, key);
        for (int i = 1; i < holds.size(); i++) {
            assertThat(holds.get(i).acquired())
                    .as("%s 잠금: %s 가 %s 이 놓기 전에 들어갔다", kind, holds.get(i).thread(), holds.get(i - 1).thread())
                    .isGreaterThan(holds.get(i - 1).released());
        }
    }

    /**
     * 요청들이 실제로 겹쳐 들어왔는지 확인한다. 첫 요청이 잠금을 놓기 전에 나머지가 모두 요청했어야
     * "동시에 눌렀다"가 성립한다. 아니면 순서대로 처리된 것이라 동시성을 검증한 것이 아니다.
     */
    private void assertAllContended(LockKind kind, long key, int expectedRequests) {
        List<Hold> holds = trace.holds(kind, key);
        assertThat(holds).hasSize(expectedRequests);
        long firstReleased = holds.get(0).released();
        assertThat(holds).as("첫 요청이 잠금을 놓기 전에 모두 잠금 앞에 와 있어야 한다")
                .allSatisfy(hold -> assertThat(hold.requested()).isLessThan(firstReleased));
    }

    /** 교착을 막기 위해 정한 순서(회원 잠금 → 매장 잠금)를 모든 요청이 지켰는지 확인한다 */
    private void assertUserLockBeforeShopLock() {
        List<Hold> shopHolds = trace.holds(LockKind.SHOP, SHOP_ID);
        List<Hold> userHolds = new ArrayList<>();
        for (int i = 0; i < PEOPLE; i++) {
            userHolds.addAll(trace.holds(LockKind.USER, FIRST_USER_ID + i));
        }
        assertThat(shopHolds).allSatisfy(shop -> assertThat(userHolds)
                .as("%s 는 매장 잠금 전에 회원 잠금을 잡았어야 한다", shop.thread())
                .anySatisfy(user -> {
                    assertThat(user.thread()).isEqualTo(shop.thread());
                    assertThat(user.acquired()).isLessThan(shop.requested());
                }));
    }

    /** 같은 회원의 요청 중 회원 잠금을 먼저 잡은 쪽이 점유에 성공했는지 확인한다 */
    private void assertFirstUserLockHolderWon(List<Outcome> outcomes, long userId) {
        String first = trace.holds(LockKind.USER, userId).get(0).thread();
        assertThat(outcomes).filteredOn(Outcome::succeeded).extracting(Outcome::thread).containsExactly(first);
    }

    /** 매장 잠금을 가장 먼저 잡은 요청이 점유에 성공하고, 나머지는 그 뒤에 들어가 막혔는지 확인한다 */
    private void assertFirstShopLockHolderWon(List<Outcome> outcomes) {
        String first = trace.holds(LockKind.SHOP, SHOP_ID).get(0).thread();
        assertThat(outcomes).filteredOn(Outcome::succeeded).extracting(Outcome::thread).containsExactly(first);
    }
}
