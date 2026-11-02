package com.ucamp.app.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.auth.dto.response.AuthPhoneResponse;
import com.ucamp.app.domain.notification.client.SmsSendStatus;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;

/** 단일 서버용 가입 인증. 재시작하면 인증이 만료되며, 여러 서버에서는 공유 저장소로 교체해야 한다. */
@Service
public class AuthPhoneVerificationService {

    private static final Duration CODE_VALIDITY = Duration.ofMinutes(3);
    private static final Duration VERIFIED_VALIDITY = Duration.ofMinutes(10);
    private static final Duration RESEND_DELAY = Duration.ofSeconds(60);
    private static final Duration SEND_WINDOW = Duration.ofHours(1);
    private static final int MAX_PHONES = 10_000;
    private final Clock clock;
    private final SolapiSmsClient smsClient;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new HashMap<>();
    private final Map<String, SendLimit> sendLimits = new HashMap<>();
    private Instant globalWindow;
    private int globalSends;

    public AuthPhoneVerificationService(Clock clock, SolapiSmsClient smsClient) {
        this.clock = clock;
        this.smsClient = smsClient;
    }

    /** 실패·응답 불명도 발송 시도로 계산해 반복 발송을 제한한다. */
    public synchronized AuthPhoneResponse send(String rawPhone) {
        String phone = digits(rawPhone);
        Instant now = clock.instant();
        challenges.values().removeIf(item -> !item.reserved && !now.isBefore(item.expiresAt));
        sendLimits.values().removeIf(item -> !now.isBefore(item.window.plus(SEND_WINDOW)));
        SendLimit limit = sendLimits.get(phone);
        if (limit != null && (now.isBefore(limit.lastSent.plus(RESEND_DELAY)) || limit.count >= 10)) {
            throw limited("인증번호 재발송은 60초 후 가능하며, 번호당 시간당 10회까지 요청할 수 있습니다.");
        }
        Challenge previous = challenges.get(phone);
        if (previous != null && previous.reserved) {
            throw invalid("가입 처리 중입니다. 잠시 후 다시 시도해 주세요.");
        }
        if (globalWindow == null || !now.isBefore(globalWindow.plus(SEND_WINDOW))) {
            globalWindow = now;
            globalSends = 0;
        }
        if (globalSends >= 100 || (limit == null && sendLimits.size() >= MAX_PHONES)) {
            throw limited("인증번호 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (limit == null) {
            limit = new SendLimit(now);
            sendLimits.put(phone, limit);
        }
        limit.lastSent = now;
        limit.count++;
        globalSends++;
        challenges.remove(phone);
        String id = UUID.randomUUID().toString();
        String code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        if (smsClient.send(phone, "[땡언니] 회원가입 인증번호 [" + code + "]를 입력해 주세요. (3분 이내)")
                .status() != SmsSendStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.AUTH_PHONE_SEND_FAILED,
                    "인증번호를 발송하지 못했습니다. 잠시 후 다시 요청해 주세요.");
        }
        Challenge challenge = new Challenge(id, hash(id, code), now.plus(CODE_VALIDITY), now.plus(RESEND_DELAY));
        challenges.put(phone, challenge);
        return response(challenge);
    }

    public synchronized AuthPhoneResponse verify(String id, String code) {
        Challenge challenge = challenges.values().stream().filter(item -> item.id.equals(id)).findFirst()
                .orElseThrow(() -> invalid("인증번호를 먼저 요청해 주세요."));
        requireLive(challenge);
        if (challenge.verified) {
            return response(challenge);
        }
        if (challenge.attempts >= 5) {
            throw invalid("인증번호 입력 횟수를 초과했습니다. 새 인증번호를 요청해 주세요.");
        }
        challenge.attempts++;
        if (!MessageDigest.isEqual(challenge.codeHash.getBytes(StandardCharsets.UTF_8),
                hash(id, code).getBytes(StandardCharsets.UTF_8))) {
            throw invalid("인증번호가 일치하지 않습니다. (최대 5회)");
        }
        challenge.verified = true;
        challenge.expiresAt = clock.instant().plus(VERIFIED_VALIDITY);
        return response(challenge);
    }

    /** 가입 트랜잭션 중에는 예약하고, 커밋하면 소비하며 롤백하면 다시 사용할 수 있게 한다. */
    public synchronized void consume(String rawPhone, String id) {
        String phone = digits(rawPhone);
        Challenge challenge = challenges.get(phone);
        if (challenge == null || !challenge.id.equals(id) || !challenge.verified || challenge.reserved) {
            throw invalid("가입할 휴대폰 번호의 인증을 완료해 주세요.");
        }
        requireLive(challenge);
        challenge.reserved = true;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            challenges.remove(phone);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                synchronized (AuthPhoneVerificationService.this) {
                    if (status == STATUS_COMMITTED) {
                        challenges.remove(phone, challenge);
                    } else {
                        challenge.reserved = false;
                    }
                }
            }
        });
    }

    private void requireLive(Challenge challenge) {
        if (!clock.instant().isBefore(challenge.expiresAt)) {
            throw invalid("휴대폰 인증이 만료되었습니다. 새 인증번호를 요청해 주세요.");
        }
    }

    private static String digits(String phone) {
        if (phone == null || !phone.matches("^01[016789]-?[0-9]{3,4}-?[0-9]{4}$")) {
            throw invalid("휴대폰 번호 형식이 올바르지 않습니다.");
        }
        return phone.replace("-", "");
    }

    private static String hash(String id, String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((id + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AuthPhoneResponse response(Challenge item) {
        ZoneOffset kst = ZoneOffset.ofHours(9);
        return new AuthPhoneResponse(item.id, item.expiresAt.atOffset(kst),
                item.resendAvailableAt.atOffset(kst), item.verified);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.AUTH_PHONE_VERIFICATION_FAILED, message);
    }

    private static BusinessException limited(String message) {
        return new BusinessException(ErrorCode.AUTH_PHONE_SEND_LIMITED, message);
    }

    private static final class Challenge {
        private final String id;
        private final String codeHash;
        private final Instant resendAvailableAt;
        private Instant expiresAt;
        private int attempts;
        private boolean verified;
        private boolean reserved;

        private Challenge(String id, String codeHash, Instant expiresAt, Instant resendAvailableAt) {
            this.id = id;
            this.codeHash = codeHash;
            this.expiresAt = expiresAt;
            this.resendAvailableAt = resendAvailableAt;
        }
    }

    private static final class SendLimit {
        private final Instant window;
        private Instant lastSent;
        private int count;

        private SendLimit(Instant now) {
            this.window = now;
        }
    }
}
