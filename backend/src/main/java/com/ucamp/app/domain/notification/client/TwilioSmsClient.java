package com.ucamp.app.domain.notification.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.twilio.exception.ApiConnectionException;
import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import lombok.extern.slf4j.Slf4j;

/**
 * Twilio 문자 API 호출.
 * 공식 SDK 로 Messages 를 만들고, 성공·실패·결과 불명을 결과로 돌려준다.
 */
@Slf4j
@Component
public class TwilioSmsClient {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final MessageSender messageSender;

    // 테스트용 생성자와 구분해 Spring 이 이 생성자를 쓰게 한다
    @Autowired
    public TwilioSmsClient(
            @Value("${twilio.account-sid}") String accountSid,
            @Value("${twilio.auth-token}") String authToken,
            @Value("${twilio.from-number}") String fromNumber) {
        this(accountSid, authToken, fromNumber, null);
    }

    /**
     * 테스트에서 가짜 발송기를 넣을 때 쓴다.
     * messageSender 가 null 이면 실제 Twilio SDK 를 호출한다.
     */
    TwilioSmsClient(String accountSid, String authToken, String fromNumber, MessageSender messageSender) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.messageSender = messageSender;
    }

    /**
     * 문자를 한 건 보낸다.
     *
     * @param toPhone     받을 번호. 010-1234-5678 또는 +821012345678
     * @param messageBody 문자 본문
     * @return 성공·실패·결과 불명. 실패·불명을 성공으로 바꾸지 않는다
     */
    public SmsSendResult send(String toPhone, String messageBody) {
        if (!StringUtils.hasText(accountSid)
                || !StringUtils.hasText(authToken)
                || !StringUtils.hasText(fromNumber)) {
            return SmsSendResult.failed("Twilio 설정(account-sid, auth-token, from-number)이 없습니다.");
        }
        if (!StringUtils.hasText(messageBody)) {
            return SmsSendResult.failed("문자 본문이 비어 있습니다.");
        }

        String toE164 = toE164(toPhone);
        if (toE164 == null) {
            return SmsSendResult.failed("수신번호 형식이 올바르지 않습니다.");
        }

        try {
            String sid = sendWithSdk(toE164, messageBody);
            if (!StringUtils.hasText(sid)) {
                return SmsSendResult.unknown("문자 API 성공 응답에 메시지 ID가 없습니다.");
            }
            return SmsSendResult.success(sid);
        } catch (ApiConnectionException e) {
            // 요청을 보낸 뒤 응답이 없으면 문자가 나갔을 수도 있어 성공·실패로 단정하지 않는다
            log.warn("Twilio 응답 없음: cause={}", e.getMessage());
            return SmsSendResult.unknown("문자 API 응답 없음: " + e.getMessage());
        } catch (ApiException e) {
            log.warn("Twilio 발송 거절: code={}, message={}", e.getCode(), e.getMessage());
            return SmsSendResult.failed(e.getMessage());
        }
    }

    private String sendWithSdk(String toE164, String messageBody) {
        if (messageSender != null) {
            return messageSender.create(toE164, fromNumber, messageBody);
        }

        TwilioRestClient client = new TwilioRestClient.Builder(accountSid, authToken).build();
        Message message = Message.creator(
                new PhoneNumber(toE164),
                new PhoneNumber(fromNumber),
                messageBody)
                .create(client);
        return message.getSid();
    }

    /**
     * 한국 휴대폰 번호를 Twilio 가 받는 E.164(+82...)로 바꾼다.
     * 이미 +로 시작하면 숫자와 +만 남긴다.
     */
    static String toE164(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.startsWith("+")) {
            String withPlus = "+" + trimmed.substring(1).replaceAll("[^0-9]", "");
            return withPlus.length() > 1 ? withPlus : null;
        }

        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.startsWith("010") && digits.length() == 11) {
            return "+82" + digits.substring(1);
        }
        if (digits.startsWith("82") && digits.length() >= 11) {
            return "+" + digits;
        }
        return null;
    }

    /**
     * Twilio 메시지 생성. 테스트에서 가짜 구현을 넣는다.
     */
    @FunctionalInterface
    interface MessageSender {
        String create(String toE164, String fromNumber, String messageBody);
    }
}
