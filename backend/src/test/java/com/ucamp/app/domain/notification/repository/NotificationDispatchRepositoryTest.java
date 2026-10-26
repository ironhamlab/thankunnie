package com.ucamp.app.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.notification.NotificationFixture;
import com.ucamp.app.domain.notification.entity.NotificationDispatch;
import com.ucamp.app.domain.notification.entity.NotificationDispatchStatus;
import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig.class)
@ActiveProfiles("test")
class NotificationDispatchRepositoryTest {

    private static final LocalDateTime SLOT_START = LocalDateTime.of(2026, 10, 23, 14, 0);
    private static final long SHOP_ID = 1L;
    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;

    @Autowired
    private NotificationDispatchRepository notificationDispatchRepository;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.shop(jdbcTemplate, SHOP_ID, 10L, "SEOUL_GANGNAM", "ACTIVE");
        NotificationFixture.user(jdbcTemplate, USER_ID);
        NotificationFixture.user(jdbcTemplate, OTHER_USER_ID);
    }

    @Test
    @DisplayName("발송 대기 작업은 PENDING 으로 저장되고 시퀀스 ID 를 받는다")
    void save_newDispatch_isPending() {
        long eventId = saveEvent(1L).getId();

        NotificationDispatch saved = notificationDispatchRepository.saveAndFlush(dispatch(eventId, USER_ID));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getStatus()).isEqualTo(NotificationDispatchStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 이벤트·회원은 두 번째 저장이 UNIQUE 제약으로 막힌다")
    void save_sameEventAndUser_violatesUnique() {
        long eventId = saveEvent(1L).getId();
        notificationDispatchRepository.saveAndFlush(dispatch(eventId, USER_ID));

        assertThatThrownBy(() -> notificationDispatchRepository.saveAndFlush(dispatch(eventId, USER_ID)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 이벤트라도 회원이 다르면 각각 한 줄씩 저장된다")
    void save_sameEventDifferentUser_savesBoth() {
        long eventId = saveEvent(1L).getId();

        notificationDispatchRepository.saveAndFlush(dispatch(eventId, USER_ID));
        notificationDispatchRepository.saveAndFlush(dispatch(eventId, OTHER_USER_ID));

        assertThat(notificationDispatchRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 회원이라도 이벤트가 다르면 각각 한 줄씩 저장된다")
    void save_sameUserDifferentEvent_savesBoth() {
        long firstEventId = saveEvent(1L).getId();
        long secondEventId = saveEvent(2L).getId();

        notificationDispatchRepository.saveAndFlush(dispatch(firstEventId, USER_ID));
        notificationDispatchRepository.saveAndFlush(dispatch(secondEventId, USER_ID));

        assertThat(notificationDispatchRepository.count()).isEqualTo(2);
    }

    private NotificationEvent saveEvent(long exceptionId) {
        return notificationEventRepository.saveAndFlush(NotificationEvent.builder()
                .eventType(NotificationEventType.IMMINENT)
                .exceptionId(exceptionId)
                .shopId(SHOP_ID)
                .slotStartsAt(SLOT_START)
                .slotEndsAt(SLOT_START.plusHours(1))
                .build());
    }

    private static NotificationDispatch dispatch(long eventId, long userId) {
        return NotificationDispatch.builder()
                .eventId(eventId)
                .userId(userId)
                .build();
    }
}
