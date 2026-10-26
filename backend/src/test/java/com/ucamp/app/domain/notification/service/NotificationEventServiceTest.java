package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.notification.NotificationFixture;
import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventStatus;
import com.ucamp.app.domain.notification.entity.NotificationEventType;
import com.ucamp.app.domain.notification.event.SlotReopenedEvent;
import com.ucamp.app.domain.notification.event.SlotReopenedEventListener;
import com.ucamp.app.domain.notification.repository.NotificationEventRepository;
import com.ucamp.app.domain.notification.repository.VacancySlotRepository;

// 재공개 이벤트는 새 트랜잭션에서 저장하므로 테스트 데이터도 커밋하고 매번 지운다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TimeConfig.class, NotificationEventService.class, VacancySlotRepository.class,
        SlotReopenedEventListener.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationEventServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 9, 0);
    private static final long SHOP_ID = 1L;
    private static final long NORMAL_TREATMENT = 100L;
    private static final long MODEL_TREATMENT = 101L;
    private static final long CANCELLED_RESERVATION = 500L;

    @Autowired
    private NotificationEventService notificationEventService;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.shop(jdbcTemplate, SHOP_ID, 10L, "SEOUL_GANGNAM", "ACTIVE");
        NotificationFixture.user(jdbcTemplate, 1L);
        NotificationFixture.user(jdbcTemplate, 2L);
        NotificationFixture.treatment(jdbcTemplate, NORMAL_TREATMENT, SHOP_ID, "NORMAL");
        NotificationFixture.treatment(jdbcTemplate, MODEL_TREATMENT, SHOP_ID, "MODEL");
    }

    @AfterEach
    void tearDown() {
        List.of("NOTIFICATION_EVENT", "SHOP_SCHEDULE_EXCEPTION", "RESERVATION", "TREATMENT", "APP_USER", "SHOP",
                "CATEGORY").forEach(table -> jdbcTemplate.update("DELETE FROM " + table));
    }

    @Test
    @DisplayName("시작 8시간 전 안으로 들어온 OPEN 빈자리에 임박 이벤트를 만든다")
    void createImminentEvents_openWithinEightHours_createsEvent() {
        openSlot(1L, NOW.plusHours(8), NOW.plusHours(10));

        int created = notificationEventService.createImminentEvents(NOW);

        assertThat(created).isEqualTo(1);
        NotificationEvent event = notificationEventRepository.findAll().get(0);
        assertThat(event.getEventType()).isEqualTo(NotificationEventType.IMMINENT);
        assertThat(event.getExceptionId()).isEqualTo(1L);
        assertThat(event.getSlotStartsAt()).isEqualTo(NOW.plusHours(8));
        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PENDING);
    }

    @Test
    @DisplayName("같은 빈자리는 여러 번 검사해도 임박 이벤트가 한 번만 생긴다")
    void createImminentEvents_runTwice_createsOnce() {
        openSlot(1L, NOW.plusHours(5), NOW.plusHours(7));

        notificationEventService.createImminentEvents(NOW);
        int secondRun = notificationEventService.createImminentEvents(NOW.plusMinutes(1));

        assertThat(secondRun).isZero();
        assertThat(notificationEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("임박 구간 전, CLOSE 로 덮인 빈자리, 접수 마감된 빈자리, 비활성 매장은 임박 이벤트 대상이 아니다")
    void createImminentEvents_notSellable_createsNothing() {
        openSlot(1L, NOW.plusHours(8).plusMinutes(30), NOW.plusHours(10));
        openSlot(2L, NOW.plusHours(1), NOW.plusHours(3));
        NotificationFixture.exception(jdbcTemplate, 3L, SHOP_ID, null, "CLOSE", NOW.plusHours(1), NOW.plusHours(3));
        // 마지막 시작 가능 시각 9:30 은 접수 마감(시작 20분 전)이 이미 지났다
        openSlot(4L, NOW.minusHours(1), NOW.plusMinutes(50));
        NotificationFixture.category(jdbcTemplate, 20L, "HAIR");
        NotificationFixture.shop(jdbcTemplate, 2L, 20L, "SEOUL_MAPO", "INACTIVE");
        NotificationFixture.exception(jdbcTemplate, 5L, 2L, null, "OPEN", NOW.plusHours(1), NOW.plusHours(2));

        assertThat(notificationEventService.createImminentEvents(NOW)).isZero();
    }

    @Test
    @DisplayName("확정·취소 처리 중·만료 전 점유와 겹치는 OPEN 은 임박 이벤트를 만들지 않는다")
    void createImminentEvents_occupied_excludesThoseSlots() {
        openSlot(1L, NOW.plusHours(1), NOW.plusHours(3));
        reservation(600L, NORMAL_TREATMENT, "CONFIRMED", NOW.plusHours(1), NOW.plusHours(2));
        openSlot(2L, NOW.plusHours(3), NOW.plusHours(5));
        reservation(601L, NORMAL_TREATMENT, "CANCEL_PENDING", NOW.plusHours(3), NOW.plusHours(4));
        openSlot(3L, NOW.plusHours(5), NOW.plusHours(7));
        NotificationFixture.reservation(jdbcTemplate, 602L, 2L, NORMAL_TREATMENT, "HOLD",
                NOW.plusHours(5), NOW.plusHours(6), NOW.plusMinutes(5));
        openSlot(4L, NOW.plusHours(7), NOW.plusHours(8));
        NotificationFixture.reservation(jdbcTemplate, 603L, 1L, NORMAL_TREATMENT, "HOLD",
                NOW.plusHours(7), NOW.plusHours(8), NOW.minusMinutes(1));

        int created = notificationEventService.createImminentEvents(NOW);

        assertThat(created).isEqualTo(1);
        assertThat(notificationEventRepository.findAll())
                .extracting(NotificationEvent::getExceptionId)
                .containsExactly(4L);
    }

    @Test
    @DisplayName("시작 3시간 전 안으로 들어온 OPEN 빈자리에 초임박 이벤트를 만든다")
    void createLastMinuteEvents_openWithinThreeHours_createsEvent() {
        openSlot(1L, NOW.plusHours(2), NOW.plusHours(4));

        int created = notificationEventService.createLastMinuteEvents(NOW);

        assertThat(created).isEqualTo(1);
        NotificationEvent event = notificationEventRepository.findAll().get(0);
        assertThat(event.getEventType()).isEqualTo(NotificationEventType.LAST_MINUTE);
        assertThat(event.getExceptionId()).isEqualTo(1L);
        assertThat(event.getReservationId()).isNull();
        assertThat(event.getSlotStartsAt()).isEqualTo(NOW.plusHours(2));
        assertThat(event.getSlotEndsAt()).isEqualTo(NOW.plusHours(4));
        assertThat(event.getStatus()).isEqualTo(NotificationEventStatus.PENDING);
    }

    @Test
    @DisplayName("같은 빈자리는 여러 번 검사해도 초임박 이벤트가 한 번만 생긴다")
    void createLastMinuteEvents_runTwice_createsOnce() {
        openSlot(1L, NOW.plusHours(2), NOW.plusHours(4));

        notificationEventService.createLastMinuteEvents(NOW);
        int secondRun = notificationEventService.createLastMinuteEvents(NOW.plusMinutes(1));

        assertThat(secondRun).isZero();
        assertThat(notificationEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("정확히 3시간 전은 초임박 이벤트를 만들고, 3시간보다 많이 남으면 만들지 않는다")
    void createLastMinuteEvents_boundary_createsOnlyWithinThreeHours() {
        openSlot(1L, NOW.plusHours(3), NOW.plusHours(5));
        openSlot(2L, NOW.plusHours(5), NOW.plusHours(7));

        int created = notificationEventService.createLastMinuteEvents(NOW);

        assertThat(created).isEqualTo(1);
        assertThat(notificationEventRepository.findAll())
                .extracting(NotificationEvent::getExceptionId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("임박 이벤트가 이미 있어도 3시간이 되면 초임박 이벤트를 따로 만든다")
    void createLastMinuteEvents_afterImminent_createsLastMinuteToo() {
        LocalDateTime startsAt = NOW.plusHours(5);
        openSlot(1L, startsAt, startsAt.plusHours(2));

        notificationEventService.createImminentEvents(NOW);
        int created = notificationEventService.createLastMinuteEvents(NOW.plusHours(2));

        assertThat(created).isEqualTo(1);
        assertThat(notificationEventRepository.findAll())
                .extracting(NotificationEvent::getEventType)
                .containsExactlyInAnyOrder(NotificationEventType.IMMINENT, NotificationEventType.LAST_MINUTE);
    }

    @Test
    @DisplayName("3시간보다 많이 남은 빈자리, CLOSE 로 덮인 빈자리, 접수 마감된 빈자리, 비활성 매장은 초임박 이벤트 대상이 아니다")
    void createLastMinuteEvents_notSellable_createsNothing() {
        openSlot(1L, NOW.plusHours(4), NOW.plusHours(6));
        openSlot(2L, NOW.plusHours(1), NOW.plusHours(3));
        NotificationFixture.exception(jdbcTemplate, 3L, SHOP_ID, null, "CLOSE", NOW.plusHours(1), NOW.plusHours(3));
        openSlot(4L, NOW.minusHours(1), NOW.plusMinutes(50));
        NotificationFixture.category(jdbcTemplate, 20L, "HAIR");
        NotificationFixture.shop(jdbcTemplate, 2L, 20L, "SEOUL_MAPO", "INACTIVE");
        NotificationFixture.exception(jdbcTemplate, 5L, 2L, null, "OPEN", NOW.plusHours(1), NOW.plusHours(2));

        assertThat(notificationEventService.createLastMinuteEvents(NOW)).isZero();
    }

    @Test
    @DisplayName("확정 점유와 겹치는 OPEN 은 초임박 이벤트를 만들지 않는다")
    void createLastMinuteEvents_occupied_createsNothing() {
        openSlot(1L, NOW.plusHours(1), NOW.plusHours(2));
        reservation(600L, NORMAL_TREATMENT, "CONFIRMED", NOW.plusHours(1), NOW.plusHours(2));

        assertThat(notificationEventService.createLastMinuteEvents(NOW)).isZero();
    }

    @Test
    @DisplayName("취소 완료 후 다시 판매 가능한 시간이면 재공개 이벤트를 한 번만 만든다")
    void createReopenedEvent_sellableAfterCancel_createsOnce() {
        openSlot(1L, NOW.plusHours(10), NOW.plusHours(14));
        reservation(CANCELLED_RESERVATION, NORMAL_TREATMENT, "CANCELLED", NOW.plusHours(11), NOW.plusHours(12));

        boolean created = notificationEventService.createReopenedEvent(CANCELLED_RESERVATION, NOW);
        boolean again = notificationEventService.createReopenedEvent(CANCELLED_RESERVATION, NOW);

        assertThat(created).isTrue();
        assertThat(again).isFalse();
        NotificationEvent event = notificationEventRepository.findAll().get(0);
        assertThat(event.getEventType()).isEqualTo(NotificationEventType.REOPENED);
        assertThat(event.getExceptionId()).isEqualTo(1L);
        assertThat(event.getReservationId()).isEqualTo(CANCELLED_RESERVATION);
        assertThat(event.getSlotStartsAt()).isEqualTo(NOW.plusHours(11));
        assertThat(event.getSlotEndsAt()).isEqualTo(NOW.plusHours(12));
    }

    @Test
    @DisplayName("환불이 끝나지 않은 취소 처리 중 예약은 재공개 이벤트를 만들지 않는다")
    void createReopenedEvent_cancelPending_returnsFalse() {
        openSlot(1L, NOW.plusHours(10), NOW.plusHours(14));
        reservation(CANCELLED_RESERVATION, NORMAL_TREATMENT, "CANCEL_PENDING", NOW.plusHours(11), NOW.plusHours(12));

        assertNoReopenedEvent();
    }

    @Test
    @DisplayName("사장님이 취소하며 시간을 닫았으면 재공개 이벤트를 만들지 않는다")
    void createReopenedEvent_closedByOwner_returnsFalse() {
        openSlot(1L, NOW.plusHours(10), NOW.plusHours(14));
        reservation(CANCELLED_RESERVATION, NORMAL_TREATMENT, "CANCELLED", NOW.plusHours(11), NOW.plusHours(12));
        NotificationFixture.exception(jdbcTemplate, 2L, SHOP_ID, CANCELLED_RESERVATION, "CLOSE",
                NOW.plusHours(11), NOW.plusHours(12));

        assertNoReopenedEvent();
    }

    @Test
    @DisplayName("다른 고객이 이미 그 시간을 잡았으면 재공개 이벤트를 만들지 않는다")
    void createReopenedEvent_occupiedByOther_returnsFalse() {
        openSlot(1L, NOW.plusHours(10), NOW.plusHours(14));
        reservation(CANCELLED_RESERVATION, NORMAL_TREATMENT, "CANCELLED", NOW.plusHours(11), NOW.plusHours(12));
        NotificationFixture.reservation(jdbcTemplate, 501L, 2L, NORMAL_TREATMENT, "HOLD",
                NOW.plusHours(11), NOW.plusHours(12), NOW.plusMinutes(5));

        assertNoReopenedEvent();
    }

    @Test
    @DisplayName("모델 시술, OPEN 밖의 시간, 접수 마감된 시간은 재공개 이벤트 대상이 아니다")
    void createReopenedEvent_notTarget_returnsFalse() {
        openSlot(1L, NOW.plusHours(10), NOW.plusHours(14));
        reservation(CANCELLED_RESERVATION, MODEL_TREATMENT, "CANCELLED", NOW.plusHours(11), NOW.plusHours(12));
        reservation(502L, NORMAL_TREATMENT, "CANCELLED", NOW.plusHours(15), NOW.plusHours(16));
        openSlot(2L, NOW, NOW.plusHours(2));
        reservation(503L, NORMAL_TREATMENT, "CANCELLED", NOW.plusMinutes(30), NOW.plusMinutes(90));

        assertThat(notificationEventService.createReopenedEvent(CANCELLED_RESERVATION, NOW)).isFalse();
        assertThat(notificationEventService.createReopenedEvent(502L, NOW)).isFalse();
        assertThat(notificationEventService.createReopenedEvent(503L, NOW.plusMinutes(10))).isFalse();
        assertThat(notificationEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("재공개 담당이 재공개 완료 이벤트를 발행하면 재공개 알림 이벤트가 저장된다")
    void slotReopenedEvent_published_createsReopenedEvent() {
        LocalDateTime startsAt = LocalDateTime.now(BookingPolicy.ZONE).plusDays(1).withHour(13)
                .withMinute(0).withSecond(0).withNano(0);
        openSlot(1L, startsAt.minusHours(1), startsAt.plusHours(3));
        reservation(CANCELLED_RESERVATION, NORMAL_TREATMENT, "CANCELLED", startsAt, startsAt.plusHours(1));

        eventPublisher.publishEvent(new SlotReopenedEvent(CANCELLED_RESERVATION));

        assertThat(notificationEventRepository.findAll())
                .extracting(NotificationEvent::getEventType)
                .containsExactly(NotificationEventType.REOPENED);
    }

    private void assertNoReopenedEvent() {
        assertThat(notificationEventService.createReopenedEvent(CANCELLED_RESERVATION, NOW)).isFalse();
        assertThat(notificationEventRepository.count()).isZero();
    }

    private void openSlot(long exceptionId, LocalDateTime startsAt, LocalDateTime endsAt) {
        NotificationFixture.exception(jdbcTemplate, exceptionId, SHOP_ID, null, "OPEN", startsAt, endsAt);
    }

    private void reservation(long reservationId, long treatmentId, String status,
            LocalDateTime startsAt, LocalDateTime endsAt) {
        NotificationFixture.reservation(jdbcTemplate, reservationId, 1L, treatmentId, status,
                startsAt, endsAt, startsAt.minusHours(1));
    }
}
