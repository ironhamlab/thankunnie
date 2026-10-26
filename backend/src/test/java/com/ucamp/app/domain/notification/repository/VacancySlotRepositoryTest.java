package com.ucamp.app.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.domain.notification.NotificationFixture;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(VacancySlotRepository.class)
@ActiveProfiles("test")
class VacancySlotRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 9, 0);
    private static final long SHOP_ID = 1L;
    private static final long TREATMENT_ID = 100L;

    @Autowired
    private VacancySlotRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.category(jdbcTemplate, 20L, "HAIR");
        NotificationFixture.shop(jdbcTemplate, SHOP_ID, 10L, "SEOUL_GANGNAM", "ACTIVE");
        NotificationFixture.shop(jdbcTemplate, 2L, 20L, "SEOUL_MAPO", "INACTIVE");
        NotificationFixture.user(jdbcTemplate, 1L);
        NotificationFixture.treatment(jdbcTemplate, TREATMENT_ID, SHOP_ID, "NORMAL");
    }

    @Test
    @DisplayName("시작까지 정확히 3시간이면 초임박만 조회하고 임박에서는 빠진다")
    void findLastMinuteWithoutEvent_exactlyThreeHours_returnsOnlyLastMinute() {
        openSlot(1L, NOW.plusHours(3), NOW.plusHours(5));

        assertThat(repository.findLastMinuteWithoutEvent(NOW))
                .extracting(VacancySlot::exceptionId)
                .containsExactly(1L);
        assertThat(repository.findImminentWithoutEvent(NOW)).isEmpty();
    }

    @Test
    @DisplayName("시작까지 3시간보다 많고 8시간 이내면 임박만 조회한다")
    void findImminentWithoutEvent_betweenThreeAndEightHours_returnsOnlyImminent() {
        openSlot(1L, NOW.plusHours(3).plusMinutes(30), NOW.plusHours(5));
        openSlot(2L, NOW.plusHours(8), NOW.plusHours(10));

        assertThat(repository.findImminentWithoutEvent(NOW))
                .extracting(VacancySlot::exceptionId)
                .containsExactly(1L, 2L);
        assertThat(repository.findLastMinuteWithoutEvent(NOW)).isEmpty();
    }

    @Test
    @DisplayName("8시간보다 많이 남은 빈자리, 휴무로 덮인 빈자리, 접수 마감된 빈자리, 비활성 매장은 초임박 대상이 아니다")
    void findLastMinuteWithoutEvent_notSellable_returnsEmpty() {
        openSlot(1L, NOW.plusHours(4), NOW.plusHours(6));
        openSlot(2L, NOW.plusHours(1), NOW.plusHours(3));
        NotificationFixture.exception(jdbcTemplate, 3L, SHOP_ID, null, "CLOSE", NOW.plusHours(1), NOW.plusHours(3));
        openSlot(4L, NOW.minusHours(1), NOW.plusMinutes(50));
        NotificationFixture.exception(jdbcTemplate, 5L, 2L, null, "OPEN", NOW.plusHours(1), NOW.plusHours(2));

        assertThat(repository.findLastMinuteWithoutEvent(NOW)).isEmpty();
        assertThat(repository.findImminentWithoutEvent(NOW))
                .extracting(VacancySlot::exceptionId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("확정·취소 처리 중·만료 전 점유와 겹치면 초임박 조회에서 빠지고, 만료된 점유는 남는다")
    void findLastMinuteWithoutEvent_occupied_excludesThoseSlots() {
        openSlot(1L, NOW.plusMinutes(30), NOW.plusHours(1));
        reservation(600L, "CONFIRMED", NOW.plusMinutes(30), NOW.plusHours(1), NOW.plusMinutes(30));
        openSlot(2L, NOW.plusHours(1), NOW.plusHours(1).plusMinutes(30));
        reservation(601L, "CANCEL_PENDING", NOW.plusHours(1), NOW.plusHours(1).plusMinutes(30),
                NOW.plusHours(1));
        openSlot(3L, NOW.plusHours(1).plusMinutes(30), NOW.plusHours(2));
        NotificationFixture.reservation(jdbcTemplate, 602L, 1L, TREATMENT_ID, "HOLD",
                NOW.plusHours(1).plusMinutes(30), NOW.plusHours(2), NOW.plusMinutes(5));
        openSlot(4L, NOW.plusHours(2), NOW.plusHours(2).plusMinutes(30));
        NotificationFixture.reservation(jdbcTemplate, 603L, 1L, TREATMENT_ID, "HOLD",
                NOW.plusHours(2), NOW.plusHours(2).plusMinutes(30), NOW.minusMinutes(1));

        assertThat(repository.findLastMinuteWithoutEvent(NOW))
                .extracting(VacancySlot::exceptionId)
                .containsExactly(4L);
    }

    @Test
    @DisplayName("초임박 이벤트가 이미 있으면 다시 조회하지 않고, 임박 이벤트만 있는 자리는 조회한다")
    void findLastMinuteWithoutEvent_existingEvent_skipsOnlyLastMinute() {
        openSlot(1L, NOW.plusHours(1), NOW.plusHours(2));
        openSlot(2L, NOW.plusHours(2), NOW.plusHours(3));
        notificationEvent(10L, "LAST_MINUTE", 1L, NOW.plusHours(1), NOW.plusHours(2));
        notificationEvent(11L, "IMMINENT", 2L, NOW.plusHours(2), NOW.plusHours(3));

        assertThat(repository.findLastMinuteWithoutEvent(NOW))
                .extracting(VacancySlot::exceptionId)
                .containsExactly(2L);
    }

    private void openSlot(long exceptionId, LocalDateTime startsAt, LocalDateTime endsAt) {
        NotificationFixture.exception(jdbcTemplate, exceptionId, SHOP_ID, null, "OPEN", startsAt, endsAt);
    }

    private void reservation(long reservationId, String status, LocalDateTime startsAt, LocalDateTime endsAt,
            LocalDateTime holdExpiresAt) {
        NotificationFixture.reservation(jdbcTemplate, reservationId, 1L, TREATMENT_ID, status,
                startsAt, endsAt, holdExpiresAt);
    }

    private void notificationEvent(long eventId, String eventType, long exceptionId,
            LocalDateTime startsAt, LocalDateTime endsAt) {
        jdbcTemplate.update("""
                INSERT INTO NOTIFICATION_EVENT (EVENT_ID, EVENT_TYPE, EXCEPTION_ID, SHOP_ID,
                                                SLOT_STARTS_AT, SLOT_ENDS_AT, STATUS, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, eventId, eventType, exceptionId, SHOP_ID, startsAt, endsAt);
    }
}
