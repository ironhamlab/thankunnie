package com.ucamp.app.domain.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

/**
 * 로그인 실패 횟수 제한. 같은 계정 종류·아이디로 연속 {@value #MAX_FAILURES}회 실패하면 일정 시간 로그인을 막는다.
 *
 * <p>없는 아이디도 똑같이 세고 막아, 잠금 응답으로 계정 존재 여부가 드러나지 않게 한다.
 * 비밀번호가 맞으면(탈퇴·승인 대기처럼 그 뒤에 거절돼도) 실패 횟수를 지운다.
 *
 * <p>단일 서버용이다 ({@link AuthPhoneVerificationService} 와 같은 방식). 재시작하면 기록이 사라지며,
 * 여러 서버로 늘리면 공유 저장소로 교체해야 한다.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(10);
    // 기록 상한. 아이디를 바꿔 가며 요청해 메모리가 늘어나는 것을 막는다
    private static final int MAX_KEYS = 10_000;

    private final Clock clock;
    private final Map<String, Attempt> attempts = new HashMap<>();

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 잠겨 있으면 비밀번호를 비교하기 전에 거절한다. */
    public synchronized void checkAllowed(LoginAccountType accountType, String loginId) {
        Attempt attempt = attempts.get(key(accountType, loginId));
        if (attempt == null || attempt.lockedUntil == null) {
            return;
        }
        Instant now = clock.instant();
        if (now.isBefore(attempt.lockedUntil)) {
            throw locked(Duration.between(now, attempt.lockedUntil));
        }
        // 잠금이 풀렸으면 실패 횟수를 처음부터 다시 센다
        attempts.remove(key(accountType, loginId));
    }

    /** 비밀번호가 틀렸거나 없는 아이디일 때. 이번 실패로 잠기면 그 자리에서 잠금 오류를 던진다. */
    public synchronized void recordFailure(LoginAccountType accountType, String loginId) {
        Instant now = clock.instant();
        String key = key(accountType, loginId);
        Attempt attempt = attempts.get(key);
        // 마지막 실패 뒤 잠금 시간만큼 지났으면 연속 실패로 보지 않는다
        if (attempt != null && !now.isBefore(attempt.lastFailedAt.plus(LOCK_DURATION))
                && attempt.lockedUntil == null) {
            attempts.remove(key);
            attempt = null;
        }
        if (attempt == null) {
            if (!hasRoom(now)) {
                // 기록이 가득 차면 새 아이디는 세지 않는다. 로그인 자체를 막으면 정상 사용자까지 못 들어온다
                return;
            }
            attempt = new Attempt();
            attempts.put(key, attempt);
        }
        attempt.failures++;
        attempt.lastFailedAt = now;
        if (attempt.failures >= MAX_FAILURES) {
            attempt.lockedUntil = now.plus(LOCK_DURATION);
            throw locked(LOCK_DURATION);
        }
    }

    /** 비밀번호가 맞으면 실패 기록을 지운다. */
    public synchronized void recordSuccess(LoginAccountType accountType, String loginId) {
        attempts.remove(key(accountType, loginId));
    }

    private boolean hasRoom(Instant now) {
        if (attempts.size() < MAX_KEYS) {
            return true;
        }
        attempts.values().removeIf(item -> item.lockedUntil == null
                ? !now.isBefore(item.lastFailedAt.plus(LOCK_DURATION))
                : !now.isBefore(item.lockedUntil));
        return attempts.size() < MAX_KEYS;
    }

    private static String key(LoginAccountType accountType, String loginId) {
        return accountType + ":" + loginId;
    }

    private static BusinessException locked(Duration remaining) {
        // 남은 시간을 분 단위로 올림해 "0분 후"가 나오지 않게 한다
        long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
        return new BusinessException(ErrorCode.AUTH_LOGIN_LOCKED, "로그인에 " + MAX_FAILURES
                + "회 실패해 잠시 로그인할 수 없습니다. " + minutes + "분 후 다시 시도해 주세요.");
    }

    /** 고객·관리자(APP_USER)와 매장(SHOP)은 아이디가 겹칠 수 있어 따로 센다. */
    public enum LoginAccountType {
        CUSTOMER, SHOP
    }

    private static final class Attempt {
        private int failures;
        private Instant lastFailedAt;
        private Instant lockedUntil;
    }
}
