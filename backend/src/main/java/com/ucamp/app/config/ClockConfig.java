package com.ucamp.app.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ucamp.app.common.policy.BookingPolicy;

/**
 * 현재 시각 기준. 시각 판단은 BookingPolicy.ZONE(Asia/Seoul) 기준이며, 테스트에서 고정 시각으로 바꿀 수 있게 빈으로 둔다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(BookingPolicy.ZONE);
    }
}
