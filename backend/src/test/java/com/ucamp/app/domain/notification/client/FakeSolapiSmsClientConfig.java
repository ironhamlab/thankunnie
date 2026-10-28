package com.ucamp.app.domain.notification.client;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 테스트에서 실제 Solapi 를 부르지 않도록 가짜 발송기를 넣는다.
 * 기본은 성공(G4V_TEST). 실패·불명 케이스는 서비스 테스트에서 Mockito 로 바꾼다.
 */
@TestConfiguration
public class FakeSolapiSmsClientConfig {

    @Bean
    SolapiSmsClient solapiSmsClient() {
        return new SolapiSmsClient(
                "api-key",
                "api-secret",
                "01000000000",
                (to, from, body) -> "G4V_TEST");
    }
}
