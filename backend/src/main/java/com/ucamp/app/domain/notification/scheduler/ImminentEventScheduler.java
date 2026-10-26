package com.ucamp.app.domain.notification.scheduler;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.domain.notification.service.NotificationEventService;

/**
 * 임박·초임박 진입 감지. 1분마다 각각 빈자리를 찾아 이벤트를 만든다.
 * 초임박 저장이 실패해도 임박 저장이 같이 취소되지 않게 메서드를 나눈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImminentEventScheduler {

    private final NotificationEventService notificationEventService;

    /** 직전 실행이 끝나고 1분 뒤 다시 실행한다. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void detectImminent() {
        try {
            int created = notificationEventService.createImminentEvents(LocalDateTime.now(BookingPolicy.ZONE));
            if (created > 0) {
                log.info("임박 이벤트 생성 {}건", created);
            }
        } catch (DataIntegrityViolationException e) {
            // 다른 실행과 겹쳐 UNIQUE 제약에 걸리면 이번 묶음은 롤백되고 다음 실행에서 다시 찾는다
            log.warn("임박 이벤트 생성 중복으로 다음 실행에서 재시도");
        }
    }

    /** 직전 실행이 끝나고 1분 뒤 다시 실행한다. 임박 감지와 트랜잭션이 따로다. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void detectLastMinute() {
        try {
            int created = notificationEventService.createLastMinuteEvents(LocalDateTime.now(BookingPolicy.ZONE));
            if (created > 0) {
                log.info("초임박 이벤트 생성 {}건", created);
            }
        } catch (DataIntegrityViolationException e) {
            // 다른 실행과 겹쳐 UNIQUE 제약에 걸리면 이번 묶음은 롤백되고 다음 실행에서 다시 찾는다
            log.warn("초임박 이벤트 생성 중복으로 다음 실행에서 재시도");
        }
    }
}
