package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.notification.NotificationFixture;
import com.ucamp.app.domain.notification.dto.response.NotificationInboxResponse;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationInboxService.class, NotificationInboxConnections.class,
        NotificationInboxQueryTest.FixedClock.class})
@ActiveProfiles("test")
class NotificationInboxQueryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 15, 0);
    private static final AuthUser CUSTOMER = TestUser.CUSTOMER_1.toAuthUser();
    private static final AuthUser OTHER_CUSTOMER = TestUser.CUSTOMER_2.toAuthUser();
    private static final AuthUser OWNER = TestUser.OWNER_1.toAuthUser();

    @Autowired
    private NotificationInboxService notificationInboxService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        NotificationFixture.user(jdbcTemplate, CUSTOMER.userId());
        NotificationFixture.user(jdbcTemplate, OTHER_CUSTOMER.userId());
    }

    @Test
    @DisplayName("본인 알림만 최신순으로 보여주고 다른 고객 알림은 빠진다")
    void getMyInbox_returnsOnlyOwnNewestFirst() {
        notificationInboxService.save(CUSTOMER.userId(), NotificationInboxCategory.RESERVATION,
                "예약이 확정되었어요", "확정 문자", "/my-reservations");
        notificationInboxService.save(CUSTOMER.userId(), NotificationInboxCategory.INTEREST,
                "임박 알림", "임박 문자", "/shops/1");
        notificationInboxService.save(OTHER_CUSTOMER.userId(), NotificationInboxCategory.RESERVATION,
                "다른 고객", "다른 고객 문자", "/my-reservations");

        assertThat(notificationInboxService.getMyInbox(CUSTOMER, null))
                .extracting(NotificationInboxResponse::title)
                .containsExactly("임박 알림", "예약이 확정되었어요");
        assertThat(notificationInboxService.getMyInbox(CUSTOMER, NotificationInboxCategory.RESERVATION))
                .extracting(NotificationInboxResponse::title)
                .containsExactly("예약이 확정되었어요");
    }

    @Test
    @DisplayName("안 읽은 수만 세고 모두 읽으면 0이 되며 이미 읽은 시각은 유지한다")
    void countUnread_andMarkAllRead_keepsFirstReadAt() {
        notificationInboxService.save(CUSTOMER.userId(), NotificationInboxCategory.RESERVATION,
                "예약이 확정되었어요", "확정 문자", "/my-reservations");
        notificationInboxService.save(OTHER_CUSTOMER.userId(), NotificationInboxCategory.RESERVATION,
                "다른 고객", "다른 고객 문자", "/my-reservations");

        assertThat(notificationInboxService.countUnread(CUSTOMER)).isEqualTo(1);

        assertThat(notificationInboxService.markAllRead(CUSTOMER)).isZero();
        OffsetDateTime firstReadAt = notificationInboxService.getMyInbox(CUSTOMER, null).get(0).readAt();
        assertThat(firstReadAt).isEqualTo(NOW.atZone(TimeUtils.KST).toOffsetDateTime());

        notificationInboxService.markAllRead(CUSTOMER);
        assertThat(notificationInboxService.getMyInbox(CUSTOMER, null).get(0).readAt()).isEqualTo(firstReadAt);
        assertThat(notificationInboxService.countUnread(OTHER_CUSTOMER)).isEqualTo(1);
    }

    @Test
    @DisplayName("사장님은 받은 알림을 조회할 수 없다")
    void getMyInbox_owner_isForbidden() {
        assertThatThrownBy(() -> notificationInboxService.getMyInbox(OWNER, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    /** 모두 읽음 시각을 NOW 로 고정한다. */
    @TestConfiguration
    @EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);
        }

        @Bean
        DateTimeProvider auditingDateTimeProvider(Clock clock) {
            return () -> java.util.Optional.of(LocalDateTime.now(clock));
        }
    }
}
