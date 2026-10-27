package com.ucamp.app.domain.notification.client;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 테스트에서 실제 Twilio 를 부르지 않도록 가짜 발송기를 넣는다.
 * 기본은 성공(SM_TEST). 실패·불명 케이스는 테스트에서 Mockito 로 바꿀 수 있다.
 */
@TestConfiguration
public class FakeTwilioSmsClientConfig {

    @Bean
    TwilioSmsClient twilioSmsClient() {
        return new TwilioSmsClient(
                "ACtest",
                "token",
                "+821011111111",
                (to, from, body) -> "SM_TEST");
    }
}
