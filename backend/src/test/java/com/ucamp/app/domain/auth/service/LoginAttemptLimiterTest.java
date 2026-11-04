package com.ucamp.app.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.auth.service.LoginAttemptLimiter.LoginAccountType;

class LoginAttemptLimiterTest {

    private static final LoginAccountType CUSTOMER = LoginAccountType.CUSTOMER;

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-04T00:00:00Z"));
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        limiter = new LoginAttemptLimiter(clock);
    }

    @Test
    @DisplayName("5회째 실패하면 잠기고, 잠긴 동안은 남은 분을 알려 주며 거절한다")
    void fifthFailure_locks() {
        failTimes(4);

        assertLocked(() -> limiter.recordFailure(CUSTOMER, "unnie"), "10분 후");
        now.set(now.get().plus(Duration.ofMinutes(9)).plusSeconds(1));
        assertLocked(() -> limiter.checkAllowed(CUSTOMER, "unnie"), "1분 후");
    }

    @Test
    @DisplayName("잠금 시간이 지나면 다시 로그인할 수 있고 실패 횟수를 처음부터 센다")
    void lockExpired_allowsAndResets() {
        failTimes(4);
        assertLocked(() -> limiter.recordFailure(CUSTOMER, "unnie"), "10분 후");

        now.set(now.get().plus(LoginAttemptLimiter.LOCK_DURATION));

        assertThatCode(() -> limiter.checkAllowed(CUSTOMER, "unnie")).doesNotThrowAnyException();
        failTimes(4);
        assertThatCode(() -> limiter.checkAllowed(CUSTOMER, "unnie")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비밀번호가 맞으면 실패 횟수를 지운다")
    void success_resetsFailures() {
        failTimes(4);
        limiter.recordSuccess(CUSTOMER, "unnie");

        failTimes(4);
        assertThatCode(() -> limiter.checkAllowed(CUSTOMER, "unnie")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("마지막 실패 뒤 잠금 시간만큼 지나면 연속 실패로 보지 않는다")
    void oldFailures_notCounted() {
        failTimes(4);
        now.set(now.get().plus(LoginAttemptLimiter.LOCK_DURATION));

        assertThatCode(() -> limiter.recordFailure(CUSTOMER, "unnie")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkAllowed(CUSTOMER, "unnie")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("고객과 매장 계정, 다른 아이디는 따로 센다")
    void separateKeys() {
        failTimes(4);

        assertThatCode(() -> limiter.recordFailure(LoginAccountType.SHOP, "unnie")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.recordFailure(CUSTOMER, "other")).doesNotThrowAnyException();
        assertLocked(() -> limiter.recordFailure(CUSTOMER, "unnie"), "10분 후");
        assertThatCode(() -> limiter.checkAllowed(LoginAccountType.SHOP, "unnie")).doesNotThrowAnyException();
    }

    private void failTimes(int count) {
        for (int i = 0; i < count; i++) {
            limiter.recordFailure(CUSTOMER, "unnie");
        }
    }

    private static void assertLocked(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String remaining) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(remaining)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_LOGIN_LOCKED);
    }
}
