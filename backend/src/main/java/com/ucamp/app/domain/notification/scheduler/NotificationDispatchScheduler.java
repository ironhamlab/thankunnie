package com.ucamp.app.domain.notification.scheduler;

import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.service.NotificationDispatchService;

/**
 * 1분마다 알림 대기 줄을 만들고, 보내기 전에 다시 확인하는 곳.
 * 빈자리를 찾는 일과 예약 임시 점유를 푸는 일과 메서드를 나눠 두었다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchScheduler {

    private final NotificationDispatchService notificationDispatchService;

    /**
     * 직전 실행이 끝나고 1분 뒤 다시 실행한다.
     * PENDING 이벤트가 없을 때까지 하나씩 대기 작업으로 저장한다.
     * 맞는 회원이 없어 만든 수가 0이어도 그 이벤트는 끝난 것이므로 다음 이벤트를 본다.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void saveDispatchJobs() {
        Optional<Integer> created = notificationDispatchService.createDispatchJobs();
        while (created.isPresent()) {
            if (created.get() > 0) {
                log.info("발송 대기 작업 생성 {}건", created.get());
            }
            created = notificationDispatchService.createDispatchJobs();
        }
    }

    /**
     * 대기 작업이 없을 때까지 한 장씩 가져온 뒤 바로 다시 확인한다.
     * 한 명이 재검사에 걸려도 다음 대기 작업은 계속 확인한다.
     * 재검사를 통과해도 문자는 보내지 않는다. 문자 발송은 이후 단계에서 붙인다.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void claimAndRecheck() {
        Optional<NotificationDispatch> claimed = notificationDispatchService.claimDispatchJob();
        while (claimed.isPresent()) {
            NotificationDispatch job = claimed.get();
            boolean canSend = notificationDispatchService.recheckClaimedJob(job);
            if (canSend) {
                log.info("발송 직전 재검사 통과 dispatchId={}. 문자 발송은 이후 단계", job.getId());
            }
            claimed = notificationDispatchService.claimDispatchJob();
        }
    }
}
