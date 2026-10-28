package com.ucamp.app.domain.shop.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopImageCleanupSchedulerTest {

    @Test
    @DisplayName("한 정리 작업의 DB 오류가 다음 작업 재시도를 막지 않는다")
    void retry_oneTaskFails_continuesOtherTasks() {
        ShopImageCleanupService service = mock(ShopImageCleanupService.class);
        when(service.findDueIds()).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("Simulated database failure")).when(service).retry(1L);
        new ShopImageCleanupScheduler(service).retryFailedDeletes();
        verify(service).retry(1L);
        verify(service).retry(2L);
    }
}
