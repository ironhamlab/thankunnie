package com.ucamp.app.domain.notification.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.solapi.sdk.message.exception.SolapiEmptyResponseException;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.model.FailedMessage;

class SolapiSmsClientTest {

    @Test
    @DisplayName("Solapi 가 메시지 ID를 주면 성공이다")
    void send_solapiAccepts_returnsSuccess() {
        SolapiSmsClient client = new SolapiSmsClient(
                "api-key",
                "api-secret",
                "01000000000",
                (to, from, body) -> "G4V20261027000000");

        SmsSendResult result = client.send("010-1234-5678", "빈자리가 생겼습니다.");

        assertThat(result.status()).isEqualTo(SmsSendStatus.SUCCESS);
        assertThat(result.providerMessageId()).isEqualTo("G4V20261027000000");
        assertThat(result.failReason()).isNull();
    }

    @Test
    @DisplayName("Solapi 가 거절하면 실패이고 사유를 남긴다")
    void send_solapiRejects_returnsFailed() {
        SolapiSmsClient client = new SolapiSmsClient(
                "api-key",
                "api-secret",
                "01000000000",
                (to, from, body) -> {
                    throw new SolapiMessageNotReceivedException("잔액이 부족합니다");
                });

        SmsSendResult result = client.send("010-1234-5678", "빈자리가 생겼습니다.");

        assertThat(result.status()).isEqualTo(SmsSendStatus.FAILED);
        assertThat(result.failReason()).contains("잔액");
        assertThat(result.providerMessageId()).isNull();
    }

    @Test
    @DisplayName("실패 목록의 statusCode·statusMessage 를 FAIL_REASON 에 남긴다")
    void send_failedMessageList_keepsStatusReason() {
        SolapiSmsClient client = new SolapiSmsClient(
                "api-key",
                "api-secret",
                "01000000000",
                (to, from, body) -> {
                    FailedMessage failed = new FailedMessage();
                    failed.setStatusCode("1062");
                    failed.setStatusMessage("발신번호 미등록");
                    SolapiMessageNotReceivedException ex =
                            new SolapiMessageNotReceivedException("개별 메시지 발송이 거절되었습니다.");
                    ex.setFailedMessageList(List.of(failed));
                    throw ex;
                });

        SmsSendResult result = client.send("010-1234-5678", "빈자리가 생겼습니다.");

        assertThat(result.status()).isEqualTo(SmsSendStatus.FAILED);
        assertThat(result.failReason()).isEqualTo("1062: 발신번호 미등록");
        assertThat(result.providerMessageId()).isNull();
    }

    @Test
    @DisplayName("실패 목록 사유는 statusCode 와 statusMessage 를 이어 붙인다")
    void formatFailedMessages_joinsCodeAndMessage() {
        FailedMessage failed = new FailedMessage();
        failed.setStatusCode("1062");
        failed.setStatusMessage("발신번호 미등록");

        assertThat(SolapiSmsClient.formatFailedMessages(List.of(failed)))
                .isEqualTo("1062: 발신번호 미등록");
        assertThat(SolapiSmsClient.formatFailedMessages(List.of())).isNull();
        assertThat(SolapiSmsClient.formatFailedMessages(null)).isNull();
    }

    @Test
    @DisplayName("Solapi 응답이 비면 결과 불명이다")
    void send_emptyResponse_returnsUnknown() {
        SolapiSmsClient client = new SolapiSmsClient(
                "api-key",
                "api-secret",
                "01000000000",
                (to, from, body) -> {
                    throw new SolapiEmptyResponseException("응답 본문이 비어 있습니다");
                });

        SmsSendResult result = client.send("010-1234-5678", "빈자리가 생겼습니다.");

        assertThat(result.status()).isEqualTo(SmsSendStatus.UNKNOWN);
        assertThat(result.failReason()).contains("응답");
        assertThat(result.providerMessageId()).isNull();
    }

    @Test
    @DisplayName("설정이 없으면 실패이다")
    void send_missingConfig_returnsFailed() {
        SolapiSmsClient client = new SolapiSmsClient("", "", "", (to, from, body) -> {
            throw new AssertionError("설정도 없이 SDK 를 부르면 안 된다");
        });

        SmsSendResult result = client.send("010-1234-5678", "빈자리가 생겼습니다.");

        assertThat(result.status()).isEqualTo(SmsSendStatus.FAILED);
        assertThat(result.failReason()).contains("Solapi 설정");
    }

    @Test
    @DisplayName("성공 응답에 ID가 없으면 결과 불명이다")
    void send_blankMessageId_returnsUnknown() {
        SolapiSmsClient client = new SolapiSmsClient(
                "api-key",
                "api-secret",
                "01000000000",
                (to, from, body) -> "  ");

        SmsSendResult result = client.send("01012345678", "빈자리가 생겼습니다.");

        assertThat(result.status()).isEqualTo(SmsSendStatus.UNKNOWN);
        assertThat(result.failReason()).contains("메시지 ID");
    }

    @Test
    @DisplayName("수신번호를 Solapi 형식(숫자만)으로 바꾼다")
    void toDigitsOnly_normalizesPhone() {
        assertThat(SolapiSmsClient.toDigitsOnly("010-1234-5678")).isEqualTo("01012345678");
        assertThat(SolapiSmsClient.toDigitsOnly("01012345678")).isEqualTo("01012345678");
        assertThat(SolapiSmsClient.toDigitsOnly("+821012345678")).isEqualTo("01012345678");
        assertThat(SolapiSmsClient.toDigitsOnly("잘못된번호")).isNull();
    }
}
