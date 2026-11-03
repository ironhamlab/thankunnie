package com.ucamp.app.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

class LocalFakeNtsBusinessClientTest {

    private static final LocalDate OPENED_ON = LocalDate.of(2020, 1, 1);
    private final LocalFakeNtsBusinessClient client = new LocalFakeNtsBusinessClient();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NtsBusinessClient.class, LocalFakeNtsBusinessClient.class);

    @Test
    @DisplayName("local 프로필에서 nts.fake=true 일 때만 실제 조회 대신 가짜 조회를 쓴다")
    void fakeMode_onlyLocalWithFlag() {
        contextRunner.withPropertyValues("spring.profiles.active=local", "nts.fake=true")
                .run(context -> assertThat(context.getBean(NtsBusinessClient.class))
                        .isInstanceOf(LocalFakeNtsBusinessClient.class));
        contextRunner.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context).doesNotHaveBean(LocalFakeNtsBusinessClient.class));
        contextRunner.withPropertyValues("spring.profiles.active=prod", "nts.fake=true")
                .run(context -> assertThat(context).doesNotHaveBean(LocalFakeNtsBusinessClient.class));
    }

    @Test
    @DisplayName("테스트 대표자명이면 국세청 호출 없이 통과한다")
    void validate_testRepresentative_passes() {
        assertThatCode(() -> client.validate("1234567891", " 테스트 대표 ", OPENED_ON)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 대표자명은 정보 불일치로 거절한다")
    void validate_otherRepresentative_rejected() {
        assertThatThrownBy(() -> client.validate("1234567891", "다른 대표", OPENED_ON))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_BUSINESS_INVALID);
    }

    @Test
    @DisplayName("휴업·폐업 테스트 번호는 거절한다")
    void validate_suspendedOrClosed_rejected() {
        assertThatThrownBy(() -> client.validate("2222222227", "테스트 대표", OPENED_ON))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("휴업·폐업");
        assertThatThrownBy(() -> client.validate("3333333336", "테스트 대표", OPENED_ON))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("휴업·폐업");
    }
}
