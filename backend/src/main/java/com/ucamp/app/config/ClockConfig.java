package com.ucamp.app.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ucamp.app.common.policy.BookingPolicy;

/**
 * 현재 시각 기준 Clock. 서비스는 LocalDateTime.now(clock) 으로 한국 시간을 얻고, 테스트는 고정 Clock 으로 바꾼다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(BookingPolicy.ZONE);
    }
}
