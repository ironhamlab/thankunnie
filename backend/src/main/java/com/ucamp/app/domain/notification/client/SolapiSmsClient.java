package com.ucamp.app.domain.notification.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.dto.response.MultipleDetailMessageSentResponse;
import com.solapi.sdk.message.exception.SolapiApiKeyException;
import com.solapi.sdk.message.exception.SolapiBadRequestException;
import com.solapi.sdk.message.exception.SolapiEmptyResponseException;
import com.solapi.sdk.message.exception.SolapiInvalidApiKeyException;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.exception.SolapiUnknownException;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;

import lombok.extern.slf4j.Slf4j;

/**
 * Solapi 문자 API 호출.
 * 공식 SDK 로 문자를 보내고, 성공·실패·결과 불명을 결과로 돌려준다.
 */
@Slf4j
@Component
public class SolapiSmsClient {

    private final String apiKey;
    private final String apiSecret;
    private final String fromNumber;
    private final MessageSender messageSender;

    // 테스트용 생성자와 구분해 Spring 이 이 생성자를 쓰게 한다
    @Autowired
    public SolapiSmsClient(
            @Value("${solapi.api-key}") String apiKey,
            @Value("${solapi.api-secret}") String apiSecret,
            @Value("${solapi.from-number}") String fromNumber) {
        this(apiKey, apiSecret, fromNumber, null);
    }

    /**
     * 테스트에서 가짜 발송기를 넣을 때 쓴다.
     * messageSender 가 null 이면 실제 Solapi SDK 를 호출한다.
     */
    SolapiSmsClient(String apiKey, String apiSecret, String fromNumber, MessageSender messageSender) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.fromNumber = fromNumber;
        this.messageSender = messageSender;
    }

    /**
     * 문자를 한 건 보낸다.
     *
     * @param toPhone     받을 번호. 010-1234-5678 또는 01012345678
     * @param messageBody 문자 본문
     * @return 성공·실패·결과 불명. 실패·불명을 성공으로 바꾸지 않는다
     */
    public SmsSendResult send(String toPhone, String messageBody) {
        if (!StringUtils.hasText(apiKey)
                || !StringUtils.hasText(apiSecret)
                || !StringUtils.hasText(fromNumber)) {
            log.warn("Solapi 설정 누락으로 발송 불가");
            return SmsSendResult.failed("Solapi 설정(api-key, api-secret, from-number)이 없습니다.");
        }
        if (!StringUtils.hasText(messageBody)) {
            log.warn("Solapi 발송 불가: 문자 본문이 비어 있음");
            return SmsSendResult.failed("문자 본문이 비어 있습니다.");
        }

        String toDigits = toDigitsOnly(toPhone);
        if (toDigits == null) {
            log.warn("Solapi 발송 불가: 수신번호 형식 오류");
            return SmsSendResult.failed("수신번호 형식이 올바르지 않습니다.");
        }
        String fromDigits = toDigitsOnly(fromNumber);
        if (fromDigits == null) {
            log.warn("Solapi 발송 불가: 발신번호 형식 오류");
            return SmsSendResult.failed("발신번호 형식이 올바르지 않습니다.");
        }

        try {
            String providerMessageId = sendWithSdk(toDigits, fromDigits, messageBody);
            if (!StringUtils.hasText(providerMessageId)) {
                log.warn("Solapi 응답에 메시지 ID 없음");
                return SmsSendResult.unknown("문자 API 성공 응답에 메시지 ID가 없습니다.");
            }
            log.info("Solapi API 호출 성공 providerMessageId={}", providerMessageId);
            return SmsSendResult.success(providerMessageId);
        } catch (SolapiMessageNotReceivedException
                | SolapiBadRequestException
                | SolapiInvalidApiKeyException
                | SolapiApiKeyException e) {
            log.warn("Solapi 발송 거절: message={}", e.getMessage());
            return SmsSendResult.failed(e.getMessage());
        } catch (SolapiEmptyResponseException | SolapiUnknownException e) {
            // 요청을 보낸 뒤 응답이 비거나 원인을 알 수 없으면 성공·실패로 단정하지 않는다
            log.warn("Solapi 응답 불명: cause={}", e.getMessage());
            return SmsSendResult.unknown("문자 API 응답 불명: " + e.getMessage());
        } catch (Exception e) {
            log.warn("Solapi 발송 중 예상 밖 오류: message={}", e.getMessage());
            return SmsSendResult.unknown("문자 API 호출 오류: " + e.getMessage());
        }
    }

    private String sendWithSdk(String toDigits, String fromDigits, String messageBody) throws Exception {
        if (messageSender != null) {
            return messageSender.create(toDigits, fromDigits, messageBody);
        }

        DefaultMessageService messageService = SolapiClient.INSTANCE.createInstance(apiKey, apiSecret);
        Message message = new Message();
        message.setTo(toDigits);
        message.setFrom(fromDigits);
        message.setText(messageBody);

        MultipleDetailMessageSentResponse response = messageService.send(message, null);
        if (response == null || response.getGroupInfo() == null) {
            return null;
        }
        return response.getGroupInfo().getGroupId();
    }

    /**
     * Solapi 가 받는 숫자만 남긴 번호로 바꾼다.
     * 010-1234-5678, +821012345678 모두 01012345678 로 맞춘다.
     */
    static String toDigitsOnly(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String digits = phone.trim().replaceAll("[^0-9]", "");
        if (digits.startsWith("82") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        if (digits.startsWith("0") && digits.length() >= 10 && digits.length() <= 11) {
            return digits;
        }
        return null;
    }

    /**
     * Solapi 메시지 생성. 테스트에서 가짜 구현을 넣는다.
     * 반환값은 문자 서비스가 준 메시지(그룹) ID 다.
     */
    @FunctionalInterface
    interface MessageSender {
        String create(String toDigits, String fromDigits, String messageBody) throws Exception;
    }
}
