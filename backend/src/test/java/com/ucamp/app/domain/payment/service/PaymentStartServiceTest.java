package com.ucamp.app.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.payment.dto.request.PaymentStartRequest;
import com.ucamp.app.domain.reservation.service.ReservationService;

class PaymentStartServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 14, 0);
    private static final long RESERVATION_ID = 10L;
    private static final AuthUser CUSTOMER = new AuthUser(1L, UserRole.CUSTOMER, null);
    private static final AuthUser OTHER = new AuthUser(2L, UserRole.CUSTOMER, null);

    private ReservationService reservationService;
    private MutableClock clock;
    private PaymentStartService service;

    @BeforeEach
    void setUp() {
        reservationService = mock(ReservationService.class);
        clock = new MutableClock(NOW.atZone(SEOUL).toInstant());
        service = new PaymentStartService(reservationService, clock);
    }

    @Test
    @DisplayName("결제를 시작하면 이 예약의 주문번호(reservation-{예약 ID}-)를 준다")
    void start_returnsMerchantUid() {
        assertThat(service.start(CUSTOMER, request("tab-1")).merchantUid()).startsWith("reservation-10-");
    }

    @Test
    @DisplayName("다른 탭이 결제 중이면 같은 계정이어도 PAYMENT_008 로 막는다")
    void start_otherTabInProgress_blocked() {
        service.start(CUSTOMER, request("tab-1"));

        assertInProgress(() -> service.start(CUSTOMER, request("tab-2")));
    }

    @Test
    @DisplayName("같은 탭이 다시 누르면(결제창을 닫고 재시도) 통과하고 주문번호도 같다")
    void start_sameTabAgain_sameMerchantUid() {
        String first = service.start(CUSTOMER, request("tab-1")).merchantUid();

        assertThat(service.start(CUSTOMER, request("tab-1")).merchantUid()).isEqualTo(first);
    }

    @Test
    @DisplayName("결제를 시작한 탭이 중단하면 다른 탭이 바로 결제할 수 있고, 주문번호는 새로 받는다")
    void abort_byStartedTab_releases() {
        String first = service.start(CUSTOMER, request("tab-1")).merchantUid();

        service.abort(CUSTOMER, request("tab-1"));

        assertThat(service.start(CUSTOMER, request("tab-2")).merchantUid()).isNotEqualTo(first);
    }

    @Test
    @DisplayName("다른 탭이나 다른 고객의 중단·확인 종료로는 진행 중 결제가 풀리지 않는다")
    void abortOrFinish_byOthers_keepsInProgress() {
        service.start(CUSTOMER, request("tab-1"));

        service.abort(CUSTOMER, request("tab-2"));
        service.abort(OTHER, request("tab-1"));
        service.finish(OTHER.userId(), RESERVATION_ID);

        assertInProgress(() -> service.start(CUSTOMER, request("tab-2")));
    }

    @Test
    @DisplayName("결제 확인이 끝나면(finish) 진행 중 표시가 풀린다")
    void finish_byOwner_releases() {
        service.start(CUSTOMER, request("tab-1"));

        service.finish(CUSTOMER.userId(), RESERVATION_ID);

        assertThat(service.start(CUSTOMER, request("tab-2")).merchantUid()).startsWith("reservation-10-");
    }

    @Test
    @DisplayName("탭을 그냥 닫아 중단 알림이 없어도 3분이 지나면 다른 탭이 이어받는다. 그 전에는 막는다")
    void start_afterTimeout_otherTabTakesOver() {
        service.start(CUSTOMER, request("tab-1"));

        clock.advance(PaymentStartService.ATTEMPT_TIMEOUT.minusSeconds(1));
        assertInProgress(() -> service.start(CUSTOMER, request("tab-2")));

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.start(CUSTOMER, request("tab-2")).merchantUid()).startsWith("reservation-10-");
    }

    @Test
    @DisplayName("같은 탭이 다시 시도하면 3분을 새로 센다")
    void start_sameTabAgain_restartsTimeout() {
        service.start(CUSTOMER, request("tab-1"));
        clock.advance(Duration.ofMinutes(2));
        service.start(CUSTOMER, request("tab-1"));

        clock.advance(Duration.ofMinutes(2));

        assertInProgress(() -> service.start(CUSTOMER, request("tab-2")));
    }

    @Test
    @DisplayName("다른 탭이 막혀도 결제 중인 탭의 주문번호와 3분 시간은 그대로다")
    void start_blockedTab_leavesInProgressAttemptUntouched() {
        String first = service.start(CUSTOMER, request("tab-1")).merchantUid();
        clock.advance(Duration.ofMinutes(2));

        assertInProgress(() -> service.start(CUSTOMER, request("tab-2")));

        // 막힌 시도가 시간을 새로 세지 않았으면 처음 시작에서 3분 뒤 다른 탭이 이어받는다
        clock.advance(Duration.ofMinutes(1));
        assertThat(service.start(CUSTOMER, request("tab-2")).merchantUid()).isNotEqualTo(first);
    }

    @Test
    @DisplayName("주기 정리는 3분이 지난 결제 시작만 지우고 진행 중인 것은 남긴다")
    void sweepTimedOut_removesOnlyTimedOut() {
        service.start(CUSTOMER, request("tab-1"));
        clock.advance(PaymentStartService.ATTEMPT_TIMEOUT);
        service.start(OTHER, new PaymentStartRequest(RESERVATION_ID + 1, "tab-9"));

        service.sweepTimedOut();

        assertThat(service.attemptCount()).isEqualTo(1);
        assertInProgress(() -> service.start(CUSTOMER, new PaymentStartRequest(RESERVATION_ID + 1, "tab-1")));
    }

    @Test
    @DisplayName("탭 값은 영문·숫자·하이픈만 받는다. 돌아올 주소에 실리므로 다른 문자는 요청 검증에서 막는다")
    void request_tabIdPattern() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertThat(validator.validate(request("8f0c2c1e-0b7a-4c43-9d55-3f1a2b4c5d6e"))).isEmpty();
            assertThat(validator.validate(request("1730700000000-k3j2h1"))).isEmpty();
            assertThat(validator.validate(request("tab&next=/evil"))).isNotEmpty();
            assertThat(validator.validate(request("a".repeat(65)))).isNotEmpty();
        }
    }

    @Test
    @DisplayName("결제할 수 없는 예약이면 결제창을 열기 전에 그 오류를 주고, 진행 중으로 남기지 않는다")
    void start_notPayable_throwsAndKeepsNothing() {
        doThrow(new BusinessException(ErrorCode.RESERVATION_HOLD_EXPIRED))
                .when(reservationService).validatePayable(anyLong(), anyLong());

        assertThatThrownBy(() -> service.start(CUSTOMER, request("tab-1")))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXPIRED);
        // 실패한 시작이 남아 있으면 다른 탭이 PAYMENT_008 을 받는다. 같은 예약 오류를 받아야 한다
        assertThatThrownBy(() -> service.start(CUSTOMER, request("tab-2")))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXPIRED);
    }

    @Test
    @DisplayName("매장 계정은 결제를 시작하거나 중단할 수 없다")
    void owner_forbidden() {
        AuthUser owner = new AuthUser(9L, UserRole.OWNER, 1L);

        assertThatThrownBy(() -> service.start(owner, request("tab-1")))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> service.abort(owner, request("tab-1")))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("같은 계정의 탭 10개가 동시에 결제를 누르면 한 탭만 통과하고 나머지는 PAYMENT_008 이다")
    void start_tenTabsAtOnce_onlyOnePasses() throws Exception {
        int tabs = 10;
        ExecutorService pool = Executors.newFixedThreadPool(tabs);
        CountDownLatch ready = new CountDownLatch(tabs);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<ErrorCode>> results = new ArrayList<>();
            for (int i = 0; i < tabs; i++) {
                String tabId = "tab-" + i;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        service.start(CUSTOMER, request(tabId));
                        return null;
                    } catch (BusinessException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<ErrorCode> codes = new ArrayList<>();
            for (Future<ErrorCode> result : results) {
                codes.add(result.get(5, TimeUnit.SECONDS));
            }

            assertThat(codes).filteredOn(code -> code == null).hasSize(1);
            assertThat(codes).filteredOn(code -> code != null)
                    .hasSize(tabs - 1)
                    .containsOnly(ErrorCode.PAYMENT_IN_PROGRESS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static PaymentStartRequest request(String tabId) {
        return new PaymentStartRequest(RESERVATION_ID, tabId);
    }

    private static void assertInProgress(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
    }

    /** 3분 시간 제한을 확인하려고 테스트 안에서 시각을 앞으로 보낸다 */
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
            return SEOUL;
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
