package com.ucamp.app.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.auth.dto.response.AuthPhoneResponse;
import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;

class AuthPhoneVerificationServiceTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-02T00:00:00Z"));
    private final AtomicReference<String> sentCode = new AtomicReference<>();
    private SolapiSmsClient smsClient;
    private AuthPhoneVerificationService service;

    @BeforeEach
    void setUp() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        smsClient = mock(SolapiSmsClient.class);
        when(smsClient.send(anyString(), anyString())).thenAnswer(invocation -> {
            var matcher = Pattern.compile("\\[([0-9]{6})\\]").matcher(invocation.<String>getArgument(1));
            assertThat(matcher.find()).isTrue();
            sentCode.set(matcher.group(1));
            return SmsSendResult.success("test-message");
        });
        service = new AuthPhoneVerificationService(clock, smsClient);
    }

    @Test
    @DisplayName("문자 발송이 느려도 다른 번호의 발송을 막지 않고, 발송 중인 같은 번호는 중복 발송하지 않는다")
    void send_slowSms_doesNotBlockOtherPhones() throws Exception {
        CountDownLatch smsStarted = new CountDownLatch(1);
        CountDownLatch releaseSms = new CountDownLatch(1);
        doAnswer(invocation -> {
            smsStarted.countDown();
            assertThat(releaseSms.await(5, TimeUnit.SECONDS)).isTrue();
            return SmsSendResult.success("slow-message");
        }).when(smsClient).send(eq("01011112222"), anyString());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AuthPhoneResponse> slow = executor.submit(() -> service.send("01011112222"));
            assertThat(smsStarted.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(service.send("01012345678").verified()).isFalse();
            assertThatThrownBy(() -> service.send("01011112222"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.AUTH_PHONE_SEND_LIMITED);

            releaseSms.countDown();
            assertThat(slow.get(5, TimeUnit.SECONDS).verified()).isFalse();
        } finally {
            releaseSms.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("인증한 번호로만 가입하며 성공한 인증은 한 번만 사용할 수 있다")
    void consume_verifiedPhone_isBoundAndSingleUse() {
        var sent = service.send("010-1234-5678");
        assertThat(sent.verified()).isFalse();
        var verified = service.verify(sent.verificationId(), sentCode.get());
        assertThat(verified.verified()).isTrue();
        assertThat(verified.expiresAt().toInstant()).isEqualTo(now.get().plus(Duration.ofMinutes(10)));
        assertThatThrownBy(() -> service.consume("01099998888", sent.verificationId()))
                .isInstanceOf(BusinessException.class);
        service.consume("01012345678", sent.verificationId());
        assertThatThrownBy(() -> service.consume("01012345678", sent.verificationId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("인증하지 않은 발송 결과로 가입할 수 없다")
    void consume_unverified_throwsException() {
        var sent = service.send("01012345678");
        assertThatThrownBy(() -> service.consume("01012345678", sent.verificationId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("3분 경계부터 인증번호가 만료된다")
    void verify_atThreeMinutes_throwsException() {
        var sent = service.send("01012345678");
        advance(Duration.ofMinutes(3));
        assertThatThrownBy(() -> service.verify(sent.verificationId(), sentCode.get()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("만료");
    }

    @Test
    @DisplayName("인증 완료 10분 후 가입할 수 없고 재확인으로 만료를 연장하지 못한다")
    void consume_afterVerifiedExpiry_throwsException() {
        var sent = service.send("01012345678");
        var verified = service.verify(sent.verificationId(), sentCode.get());
        advance(Duration.ofMinutes(9));
        assertThat(service.verify(sent.verificationId(), sentCode.get()).expiresAt()).isEqualTo(verified.expiresAt());
        advance(Duration.ofMinutes(1));
        assertThatThrownBy(() -> service.consume("01012345678", sent.verificationId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("만료");
    }

    @Test
    @DisplayName("오입력 5회 이후에는 올바른 번호도 거절한다")
    void verify_afterFiveFailures_throwsException() {
        var sent = service.send("01012345678");
        String wrong = sentCode.get().equals("000000") ? "000001" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verify(sent.verificationId(), wrong))
                    .isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> service.verify(sent.verificationId(), sentCode.get()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("횟수");
    }

    @Test
    @DisplayName("60초 이전 재발송을 막고 재발송하면 이전 인증을 무효화한다")
    void send_afterCooldown_invalidatesPreviousChallenge() {
        var first = service.send("01012345678");
        String firstCode = sentCode.get();
        assertThatThrownBy(() -> service.send("010-1234-5678"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("60초");
        advance(Duration.ofSeconds(60));
        var second = service.send("01012345678");
        assertThat(second.verificationId()).isNotEqualTo(first.verificationId());
        assertThatThrownBy(() -> service.verify(first.verificationId(), firstCode))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("번호당 시간당 10회 발송 제한을 적용한다")
    void send_afterTenRequests_throwsException() {
        for (int index = 0; index < 10; index++) {
            service.send("01012345678");
            advance(Duration.ofSeconds(60));
        }
        assertThatThrownBy(() -> service.send("01012345678"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("10회");
        advance(Duration.ofHours(1));
        assertThat(service.send("01012345678")).isNotNull();
    }

    @Test
    @DisplayName("문자 응답 불명은 발송 성공으로 처리하지 않는다")
    void send_unknownProviderResult_throwsException() {
        doReturn(SmsSendResult.unknown("timeout")).when(smsClient).send(anyString(), anyString());
        assertThatThrownBy(() -> service.send("01012345678"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_PHONE_SEND_FAILED));
    }

    @Test
    @DisplayName("가입 중 중복 소비를 막고 롤백 시 인증을 복구하며 커밋 시 삭제한다")
    void consume_transactionCompletion_restoresOrDeletesChallenge() {
        AuthPhoneResponse sent = service.send("01012345678");
        service.verify(sent.verificationId(), sentCode.get());
        completeTransaction(sent, TransactionSynchronization.STATUS_ROLLED_BACK);
        completeTransaction(sent, TransactionSynchronization.STATUS_COMMITTED);
        assertThatThrownBy(() -> service.consume("01012345678", sent.verificationId()))
                .isInstanceOf(BusinessException.class);
    }

    private void completeTransaction(AuthPhoneResponse sent, int status) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.consume("01012345678", sent.verificationId());
            assertThatThrownBy(() -> service.consume("01012345678", sent.verificationId()))
                    .isInstanceOf(BusinessException.class);
            TransactionSynchronizationManager.getSynchronizations().forEach(item -> item.afterCompletion(status));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void advance(Duration duration) {
        now.updateAndGet(value -> value.plus(duration));
    }
}
