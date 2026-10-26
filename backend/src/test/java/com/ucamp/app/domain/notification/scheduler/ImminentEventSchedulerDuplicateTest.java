package com.ucamp.app.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.ucamp.app.domain.notification.service.NotificationEventService;

@ExtendWith(MockitoExtension.class)
class ImminentEventSchedulerDuplicateTest {

    @Mock
    private NotificationEventService notificationEventService;

    private ImminentEventScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ImminentEventScheduler(notificationEventService);
    }

    @Test
    @DisplayName("초임박 저장이 중복이면 예외를 삼키고 임박 저장은 호출하지 않는다")
    void detectLastMinute_duplicate_doesNotCallImminent() {
        given(notificationEventService.createLastMinuteEvents(any()))
                .willThrow(new DataIntegrityViolationException("중복"));

        scheduler.detectLastMinute();

        verify(notificationEventService, never()).createImminentEvents(any());
    }

    @Test
    @DisplayName("임박 저장이 중복이면 예외를 삼키고 초임박 저장은 호출하지 않는다")
    void detectImminent_duplicate_doesNotCallLastMinute() {
        given(notificationEventService.createImminentEvents(any()))
                .willThrow(new DataIntegrityViolationException("중복"));

        scheduler.detectImminent();

        verify(notificationEventService, never()).createLastMinuteEvents(any());
    }

    @Test
    @DisplayName("초임박 저장의 중복이 아닌 오류는 그대로 던진다")
    void detectLastMinute_otherError_throws() {
        given(notificationEventService.createLastMinuteEvents(any()))
                .willThrow(new IllegalStateException("저장 실패"));

        assertThatThrownBy(() -> scheduler.detectLastMinute())
                .isInstanceOf(IllegalStateException.class);
    }
}
