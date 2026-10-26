package com.ucamp.app.domain.reservation.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 예약 점유 만료. 알림 스케줄과 메서드를 나눈다.
 * 문자 발송이 늦어도 이 메서드는 그 안에서 기다리지 않는다.
 * 만료 규칙은 예약 담당이 만든다. 여기서는 호출 자리만 둔다.
 */
@Slf4j
@Component
public class ReservationHoldExpireScheduler {

    /** 직전 실행이 끝나고 1분 뒤 다시 실행한다. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000, scheduler = ReservationHoldExpireSchedulerConfig.SCHEDULER)
    public void expireHolds() {
        // 예약 담당 만료 메서드가 아직 없다. 생기면 이 자리에서만 호출한다.
        log.debug("예약 점유 만료 호출 자리. 예약 담당 메서드가 아직 없다");
    }
}
