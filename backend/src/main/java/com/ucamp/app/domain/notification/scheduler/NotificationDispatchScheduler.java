package com.ucamp.app.domain.notification.scheduler;

import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.service.NotificationDispatchService;

/**
 * 10초마다 알림 대기 줄을 만들고, 보내기 전에 다시 확인하는 곳.
 * 카테고리·지역 알림은 60분이 지난 묶음을 1분마다 여기서 비운다.
 * 빈자리를 찾는 일과 예약 임시 점유를 푸는 일과 메서드를 나눠 두었다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchScheduler {

    private final NotificationDispatchService notificationDispatchService;

    /**
     * 직전 실행이 끝나고 10초 뒤 다시 실행한다.
     * PENDING 이벤트가 없을 때까지 하나씩 대기 작업으로 저장한다.
     * 맞는 회원이 없어 만든 수가 0이어도 그 이벤트는 끝난 것이므로 다음 이벤트를 본다.
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
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
     * 대기 작업이 없을 때까지 한 장씩 가져온 뒤 다시 확인하고, 통과하면 문자를 보낸다.
     * 한 명이 재검사에 걸려도 다음 대기 작업은 계속 확인한다.
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void claimAndRecheck() {
        Optional<NotificationDispatch> claimed = notificationDispatchService.claimDispatchJob();
        while (claimed.isPresent()) {
            NotificationDispatch job = claimed.get();
            log.info("발송 작업 선점 dispatchId={}, userId={}, eventId={}",
                    job.getId(), job.getUserId(), job.getEventId());
            boolean canSend = notificationDispatchService.recheckClaimedJob(job);
            if (canSend) {
                notificationDispatchService.sendClaimedJob(job);
            }
            claimed = notificationDispatchService.claimDispatchJob();
        }
    }

    /**
     * 직전 실행이 끝나고 1분 뒤 다시 실행한다.
     * 카테고리·지역 알림 중 마지막 발송 시각이 60분을 넘긴 것만 묶음 문자로 비운다.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void flushCategoryRegionSummaries() {
        int sent = notificationDispatchService.flushCategoryRegionSummaries();
        if (sent > 0) {
            log.info("카테고리 묶음 문자 {}통", sent);
        }
    }
}
