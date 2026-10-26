package com.ucamp.app.domain.shop.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 실패한 이미지 정리를 재개한다. 한 작업 오류가 다른 작업 처리를 막지 않도록 분리한다. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ShopImageCleanupScheduler {

    private final ShopImageCleanupService service;

    public ShopImageCleanupScheduler(ShopImageCleanupService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void retryFailedDeletes() {
        for (Long cleanupId : service.findDueIds()) {
            try {
                service.retry(cleanupId);
            } catch (RuntimeException e) {
                log.error("매장 이미지 정리 작업 처리 실패: {}", cleanupId, e);
            }
        }
    }
}
