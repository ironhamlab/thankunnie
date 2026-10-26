package com.ucamp.app.domain.shop.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ucamp.app.domain.shop.entity.ShopImageCleanup;
import com.ucamp.app.domain.shop.repository.ShopImageCleanupRepository;
import com.ucamp.app.domain.shop.repository.ShopRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopImageCleanupServiceTest {

    private static final String SCOPE = "s3:test-bucket/shop-images/";
    private static final String FILE_NAME = "12345678-1234-1234-1234-123456789012.png";

    @Mock
    private ShopImageCleanupRepository repository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private ShopImageObjectStorage objectStorage;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LocalDateTime now = LocalDateTime.now(clock);
    private ShopImageCleanupService service;

    @BeforeEach
    void setUp() {
        service = new ShopImageCleanupService(repository, shopRepository, objectStorage, clock);
    }

    @Test
    @DisplayName("재시도 실패는 작업을 유지하고 대기 간격을 늘리며 예정 시각 전에는 다시 삭제하지 않는다")
    void retry_storageFailure_backsOffWithoutRemovingTask() {
        ShopImageCleanup task = new ShopImageCleanup(SCOPE, FILE_NAME, now.minusMinutes(2));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(objectStorage.scope()).thenReturn(SCOPE);
        doThrow(new IllegalStateException("Simulated failure")).when(objectStorage).delete(FILE_NAME);
        service.retry(1L);
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getNextAttemptAt()).isEqualTo(now.plusMinutes(2));
        service.retry(1L);
        verify(objectStorage).delete(FILE_NAME);
        verify(repository, never()).delete(task);
    }

    @Test
    @DisplayName("다른 저장소 작업은 현재 설정의 파일을 삭제하지 않는다")
    void retry_differentScope_doesNotDelete() {
        ShopImageCleanup task = new ShopImageCleanup("s3:other-bucket/shop-images/", FILE_NAME, now.minusMinutes(2));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(objectStorage.scope()).thenReturn(SCOPE);
        service.retry(1L);
        verify(objectStorage, never()).delete(FILE_NAME);
        verify(repository, never()).delete(task);
    }

    @Test
    @DisplayName("현재 DB에서 참조 중인 이미지의 정리 작업은 파일 삭제 없이 취소한다")
    void retry_referencedImage_keepsFile() {
        ShopImageCleanup task = new ShopImageCleanup(SCOPE, FILE_NAME, now.minusMinutes(2));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(task));
        when(objectStorage.scope()).thenReturn(SCOPE);
        when(shopRepository.existsByImageUrlEndingWith("/images/" + FILE_NAME)).thenReturn(true);
        service.retry(1L);
        verify(objectStorage, never()).delete(FILE_NAME);
        verify(repository).delete(task);
    }

    @Test
    @DisplayName("반복 실패해도 재시도 대기는 최대 1시간으로 제한한다")
    void reschedule_repeatedFailure_capsDelay() {
        ShopImageCleanup task = new ShopImageCleanup(SCOPE, FILE_NAME, now);
        for (int attempt = 0; attempt < 10; attempt++) {
            task.reschedule(now);
        }
        assertThat(task.getAttemptCount()).isEqualTo(10);
        assertThat(task.getNextAttemptAt()).isEqualTo(now.plusHours(1));
    }
}
