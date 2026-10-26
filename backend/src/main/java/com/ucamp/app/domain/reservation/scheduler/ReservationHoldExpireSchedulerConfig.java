package com.ucamp.app.domain.reservation.scheduler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 예약 점유 만료만 도는 스케줄 스레드.
 * 기본 스케줄 스레드는 하나라, 알림 작업이 오래 걸려도 만료가 그 뒤에서 기다리지 않게 나눈다.
 */
@Configuration
public class ReservationHoldExpireSchedulerConfig {

    public static final String SCHEDULER = "reservationHoldExpireTaskScheduler";

    @Bean(name = SCHEDULER)
    public TaskScheduler reservationHoldExpireTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("reservation-hold-expire-");
        return scheduler;
    }
}
