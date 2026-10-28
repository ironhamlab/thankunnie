package com.ucamp.app.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.EnumSet;

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
        assertThat(saved.getFailReason()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("발송 완료로 바꾸면 SENT 이고 실패 사유는 비운다")
    void markSent_clearsFailReason() {
        NotificationDispatch saved = notificationDispatchRepository.saveAndFlush(dispatch(saveEvent(1L).getId(), USER_ID));
        saved.markFailed("잠깐 실패");
        notificationDispatchRepository.saveAndFlush(saved);

        saved.markSent();
        notificationDispatchRepository.saveAndFlush(saved);

        NotificationDispatch found = notificationDispatchRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(NotificationDispatchStatus.SENT);
        assertThat(found.getFailReason()).isNull();
    }

    @Test
    @DisplayName("발송 실패로 바꾸면 FAILED 와 오류 사유를 남긴다")
    void markFailed_savesFailReason() {
        NotificationDispatch saved = notificationDispatchRepository.saveAndFlush(dispatch(saveEvent(1L).getId(), USER_ID));

        saved.markFailed("수신번호 형식 오류");
        notificationDispatchRepository.saveAndFlush(saved);

        NotificationDispatch found = notificationDispatchRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(NotificationDispatchStatus.FAILED);
        assertThat(found.getFailReason()).isEqualTo("수신번호 형식 오류");
    }

    @Test
    @DisplayName("결과 불명으로 바꾸면 UNKNOWN 과 확인이 필요한 사유를 남긴다")
    void markUnknown_savesFailReason() {
        NotificationDispatch saved = notificationDispatchRepository.saveAndFlush(dispatch(saveEvent(1L).getId(), USER_ID));

        saved.markUnknown("문자 API 응답 시간 초과");
        notificationDispatchRepository.saveAndFlush(saved);

        NotificationDispatch found = notificationDispatchRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(NotificationDispatchStatus.UNKNOWN);
        assertThat(found.getFailReason()).isEqualTo("문자 API 응답 시간 초과");
    }

    @Test
    @DisplayName("같은 이벤트·회원에 SENT 또는 UNKNOWN 이 있는지 확인할 수 있다")
    void existsByEventIdAndUserIdAndStatusIn_sentOrUnknown() {
        long eventId = saveEvent(1L).getId();
        NotificationDispatch saved = notificationDispatchRepository.saveAndFlush(dispatch(eventId, USER_ID));

        assertThat(notificationDispatchRepository.existsByEventIdAndUserIdAndStatusIn(
                eventId, USER_ID, EnumSet.of(NotificationDispatchStatus.SENT, NotificationDispatchStatus.UNKNOWN)))
                .isFalse();

        saved.markSent();
        notificationDispatchRepository.saveAndFlush(saved);

        assertThat(notificationDispatchRepository.existsByEventIdAndUserIdAndStatusIn(
                eventId, USER_ID, EnumSet.of(NotificationDispatchStatus.SENT, NotificationDispatchStatus.UNKNOWN)))
                .isTrue();
        assertThat(notificationDispatchRepository.existsByEventIdAndUserIdAndStatusIn(
                eventId, OTHER_USER_ID, EnumSet.of(NotificationDispatchStatus.SENT, NotificationDispatchStatus.UNKNOWN)))
                .isFalse();
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

    @Test
    @DisplayName("대기 작업을 처리 중으로 바꾸는 수정은 한 번만 성공한다")
    void claimPending_secondUpdate_changesNoRow() {
        long eventId = saveEvent(1L).getId();
        NotificationDispatch saved = notificationDispatchRepository.saveAndFlush(dispatch(eventId, USER_ID));

        int first = notificationDispatchRepository.claimPending(
                saved.getId(),
                NotificationDispatchStatus.PENDING,
                NotificationDispatchStatus.PROCESSING,
                SLOT_START);
        int second = notificationDispatchRepository.claimPending(
                saved.getId(),
                NotificationDispatchStatus.PENDING,
                NotificationDispatchStatus.PROCESSING,
                SLOT_START);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(notificationDispatchRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationDispatchStatus.PROCESSING);
    }

    private NotificationEvent saveEvent(long exceptionId) {
        // 유니크가 매장+종류+시작 시각이라, 이벤트마다 시작 시각을 다르게 둔다.
        LocalDateTime startsAt = SLOT_START.plusHours(exceptionId - 1);
        return notificationEventRepository.saveAndFlush(NotificationEvent.builder()
                .eventType(NotificationEventType.IMMINENT)
                .exceptionId(exceptionId)
                .shopId(SHOP_ID)
                .slotStartsAt(startsAt)
                .slotEndsAt(startsAt.plusHours(1))
                .build());
    }

    private static NotificationDispatch dispatch(long eventId, long userId) {
        return NotificationDispatch.builder()
                .eventId(eventId)
                .userId(userId)
                .build();
    }
}
