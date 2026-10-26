package com.ucamp.app.domain.notification.scheduler;

import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.service.NotificationDispatchService;

/**
 * 발송 대기 작업 저장과 선점. 임박 감지, 예약 점유 만료와 메서드를 나눈다.
 * 이 작업이 늦어도 만료 스케줄이 같은 메서드 안에서 기다리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchScheduler {

    private final NotificationDispatchService notificationDispatchService;

    /** 직전 실행이 끝나고 1분 뒤 다시 실행한다. 한 번에 PENDING 이벤트 하나만 작업으로 저장한다. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void saveDispatchJobs() {
        int created = notificationDispatchService.createDispatchJobs();
        if (created > 0) {
            log.info("발송 대기 작업 생성 {}건", created);
        }
    }

    /**
     * 대기 작업 하나를 가져온 뒤 바로 다시 확인한다.
     * 재검사를 통과해도 문자는 보내지 않는다. 문자 발송은 7단계다.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void claimAndRecheck() {
        Optional<NotificationDispatch> claimed = notificationDispatchService.claimDispatchJob();
        if (claimed.isEmpty()) {
            return;
        }

        NotificationDispatch job = claimed.get();
        boolean canSend = notificationDispatchService.recheckClaimedJob(job);
        if (canSend) {
            log.info("발송 직전 재검사 통과 dispatchId={}. 문자 발송은 7단계", job.getId());
        }
    }
}
