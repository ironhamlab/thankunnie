package com.ucamp.app.domain.notification.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.twilio.exception.ApiConnectionException;
import com.twilio.exception.ApiException;

class TwilioSmsClientTest {

    private static final String ACCOUNT_SID = "ACtest";
    private static final String AUTH_TOKEN = "token";
    private static final String FROM_NUMBER = "+15005550006";

    @Test
    @DisplayName("Twilio 가 메시지 ID를 주면 성공이다")
    void send_twilioAccepts_returnsSuccess() {
        TwilioSmsClient client = new TwilioSmsClient(
                ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER,
                (to, from, body) -> "SM123");

        SmsSendResult result = client.send("010-1234-5678", "빈자리가 생겼습니다.");

        assertThat(result.status()).isEqualTo(SmsSendStatus.SUCCESS);
        assertThat(result.providerMessageId()).isEqualTo("SM123");
        assertThat(result.failReason()).isNull();
    }

    @Test
    @DisplayName("Twilio 가 거절하면 실패이고 사유를 남긴다")
    void send_twilioRejects_returnsFailed() {
        TwilioSmsClient client = new TwilioSmsClient(
                ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER,
                (to, from, body) -> {
                    throw new ApiException("Invalid 'To' Phone Number", 21211);
                });

        SmsSendResult result = client.send("010-1234-5678", "빈자리가 생겼습니다.");

        assertThat(result.status()).isEqualTo(SmsSendStatus.FAILED);
        assertThat(result.failReason()).contains("Invalid 'To' Phone Number");
        assertThat(result.providerMessageId()).isNull();
    }

    @Test
    @DisplayName("응답을 받지 못하면 결과 불명이다. 성공으로 보지 않는다")
    void send_noResponse_returnsUnknown() {
        TwilioSmsClient client = new TwilioSmsClient(
                ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER,
                (to, from, body) -> {
                    throw new ApiConnectionException("Read timed out");
                });

        SmsSendResult result = client.send("010-1234-5678", "빈자리가 생겼습니다.");

        assertThat(result.status()).isEqualTo(SmsSendStatus.UNKNOWN);
        assertThat(result.failReason()).contains("응답 없음");
        assertThat(result.providerMessageId()).isNull();
    }

    @Test
    @DisplayName("설정이 비어 있으면 API 를 부르지 않고 실패한다")
    void send_missingConfig_returnsFailedWithoutApiCall() {
        TwilioSmsClient client = new TwilioSmsClient("", "", "", (to, from, body) -> {
            throw new AssertionError("설정을 확인하기 전에 API 를 부르면 안 된다");
        });

        SmsSendResult result = client.send("010-1234-5678", "빈자리가 생겼습니다.");

        assertThat(result.status()).isEqualTo(SmsSendStatus.FAILED);
        assertThat(result.failReason()).contains("Twilio 설정");
    }

    @Test
    @DisplayName("한국 휴대폰 번호는 +82 형식으로 바꾼다")
    void toE164_koreanMobile_addsCountryCode() {
        assertThat(TwilioSmsClient.toE164("010-1234-5678")).isEqualTo("+821012345678");
        assertThat(TwilioSmsClient.toE164("01012345678")).isEqualTo("+821012345678");
        assertThat(TwilioSmsClient.toE164("+821012345678")).isEqualTo("+821012345678");
        assertThat(TwilioSmsClient.toE164("잘못된번호")).isNull();
    }
}
