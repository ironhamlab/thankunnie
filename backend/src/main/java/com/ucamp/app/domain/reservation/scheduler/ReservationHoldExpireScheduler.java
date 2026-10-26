package com.ucamp.app.domain.reservation.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.reservation.service.ReservationService;

/**
 * 예약 점유 만료. 알림 스케줄과 메서드를 나눈다.
 * 문자 발송이 늦어도 이 메서드는 그 안에서 기다리지 않는다.
 * 만료 규칙은 예약 담당 메서드에 있고, 여기서는 그 메서드만 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationHoldExpireScheduler {

    private final ReservationService reservationService;

    /** 직전 실행이 끝나고 1분 뒤 다시 실행한다. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000, scheduler = ReservationHoldExpireSchedulerConfig.SCHEDULER)
    public void expireHolds() {
        int expired = reservationService.expireHolds();
        if (expired > 0) {
            log.info("점유 만료 {}건", expired);
        }
    }
}
