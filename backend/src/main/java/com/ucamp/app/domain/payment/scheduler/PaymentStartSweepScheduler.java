package com.ucamp.app.domain.payment.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.domain.payment.service.PaymentStartService;
import com.ucamp.app.domain.reservation.scheduler.ReservationHoldExpireSchedulerConfig;

/**
 * 탭을 닫아 중단 알림이 오지 않은 결제 시작 기록을 메모리에서 지운다.
 * 예약 대기열 정리처럼 짧게 끝나는 작업이라 점유 만료와 같은 스케줄 스레드를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class PaymentStartSweepScheduler {

    private final PaymentStartService paymentStartService;

    /** 직전 실행이 끝나고 1분 뒤 다시 실행한다. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000, scheduler = ReservationHoldExpireSchedulerConfig.SCHEDULER)
    public void sweepTimedOut() {
        paymentStartService.sweepTimedOut();
    }
}
