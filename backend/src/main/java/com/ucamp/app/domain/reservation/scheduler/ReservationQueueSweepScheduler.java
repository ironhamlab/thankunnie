package com.ucamp.app.domain.reservation.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.domain.reservation.service.ReservationQueueService;

/**
 * 아무도 기다리지 않는 예약 대기열을 메모리에서 지운다.
 * 짧게 끝나는 작업이라 점유 만료와 같은 스케줄 스레드를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class ReservationQueueSweepScheduler {

    private final ReservationQueueService reservationQueueService;

    /** 직전 실행이 끝나고 1분 뒤 다시 실행한다. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000, scheduler = ReservationHoldExpireSchedulerConfig.SCHEDULER)
    public void sweepIdleQueues() {
        reservationQueueService.sweepIdleQueues();
    }
}
