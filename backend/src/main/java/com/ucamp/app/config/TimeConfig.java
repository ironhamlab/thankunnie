package com.ucamp.app.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.ucamp.app.common.util.TimeUtils;

/**
 * 서비스 기준 시각 설정.
 *
 * <p>모든 현재 시각은 이 {@link Clock}으로 구한다. JPA Auditing(createdAt, updatedAt)도
 * 같은 Clock을 사용하므로, 테스트에서 Clock을 고정하면 감사 시각도 함께 고정된다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.system(TimeUtils.KST);
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}