package com.ucamp.app.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.notification.service.NotificationEventService;

@ExtendWith(MockitoExtension.class)
class ImminentEventSchedulerDuplicateTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 23, 9, 0).atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);

    @Mock
    private NotificationEventService notificationEventService;

    private ImminentEventScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ImminentEventScheduler(notificationEventService, CLOCK);
    }

    @Test
    @DisplayName("초임박 저장이 중복이면 예외를 삼키고 임박 저장은 호출하지 않는다")
    void detectLastMinute_duplicate_doesNotCallImminent() {
        given(notificationEventService.createLastMinuteEvents(any()))
                .willThrow(new DuplicateKeyException("중복"));

        scheduler.detectLastMinute();

        verify(notificationEventService, never()).createImminentEvents(any());
    }

    @Test
    @DisplayName("임박 저장이 중복이면 예외를 삼키고 초임박 저장은 호출하지 않는다")
    void detectImminent_duplicate_doesNotCallLastMinute() {
        given(notificationEventService.createImminentEvents(any()))
                .willThrow(new DuplicateKeyException("중복"));

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

    @Test
    @DisplayName("초임박 저장의 중복이 아닌 제약 오류는 그대로 던진다")
    void detectLastMinute_otherConstraint_throws() {
        DataIntegrityViolationException error = new DataIntegrityViolationException("없는 매장");
        given(notificationEventService.createLastMinuteEvents(any())).willThrow(error);

        assertThatThrownBy(() -> scheduler.detectLastMinute()).isSameAs(error);
    }

    @Test
    @DisplayName("JPA가 초임박 중복을 DataIntegrityViolationException으로 주면 넘어간다")
    void detectLastMinute_jpaDuplicateConstraint_doesNotThrow() {
        given(notificationEventService.createLastMinuteEvents(any()))
                .willThrow(duplicateSlotViolation());

        scheduler.detectLastMinute();
    }

    @Test
    @DisplayName("JPA가 임박 중복을 DataIntegrityViolationException으로 주면 넘어간다")
    void detectImminent_jpaDuplicateConstraint_doesNotThrow() {
        given(notificationEventService.createImminentEvents(any()))
                .willThrow(duplicateSlotViolation());

        scheduler.detectImminent();
    }

    @Test
    @DisplayName("다른 제약 이름으로 온 오류는 그대로 던진다")
    void detectLastMinute_otherConstraintName_throws() {
        SQLException sql = new SQLException("fk", "23503");
        ConstraintViolationException hibernate = new ConstraintViolationException(
                "없는 매장", sql, "FK_NOTIFICATION_EVENT_SHOP");
        DataIntegrityViolationException error = new DataIntegrityViolationException("없는 매장", hibernate);
        given(notificationEventService.createLastMinuteEvents(any())).willThrow(error);

        assertThatThrownBy(() -> scheduler.detectLastMinute()).isSameAs(error);
    }

    private static DataIntegrityViolationException duplicateSlotViolation() {
        SQLException sql = new SQLException("unique", "23505");
        ConstraintViolationException hibernate = new ConstraintViolationException(
                "중복", sql, "UK_NOTIFICATION_EVENT_SLOT");
        return new DataIntegrityViolationException("중복", hibernate);
    }
}
