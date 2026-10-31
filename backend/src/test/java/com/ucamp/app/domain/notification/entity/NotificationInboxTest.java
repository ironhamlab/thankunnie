package com.ucamp.app.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.notification.NotificationFixture;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig.class)
@ActiveProfiles("test")
class NotificationInboxTest {

    private static final long USER_ID = 1L;
    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 10, 31, 15, 0);

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        NotificationFixture.user(jdbcTemplate, USER_ID);
    }

    @Test
    @DisplayName("받은 알림은 안 읽은 상태로 저장되고 종류는 문자열로 남는다")
    void save_newInbox_isUnread() {
        NotificationInbox saved = testEntityManager.persistAndFlush(inbox(
                NotificationInboxCategory.RESERVATION, "/my-reservations"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getCategory()).isEqualTo(NotificationInboxCategory.RESERVATION);
        assertThat(saved.getReadAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        String category = jdbcTemplate.queryForObject(
                "SELECT CATEGORY FROM NOTIFICATION_INBOX WHERE INBOX_ID = ?",
                String.class,
                saved.getId());
        assertThat(category).isEqualTo("RESERVATION");
    }

    @Test
    @DisplayName("이동 경로가 없는 관심 알림도 저장된다")
    void save_interestWithoutLink_allowsNullLinkPath() {
        NotificationInbox saved = testEntityManager.persistAndFlush(inbox(NotificationInboxCategory.INTEREST, null));

        assertThat(saved.getCategory()).isEqualTo(NotificationInboxCategory.INTEREST);
        assertThat(saved.getLinkPath()).isNull();
    }

    @Test
    @DisplayName("읽음 처리는 한 번만 시각을 적고 다시 호출해도 처음 시각을 유지한다")
    void markRead_setsReadAtOnlyOnce() {
        NotificationInbox saved = testEntityManager.persistAndFlush(inbox(
                NotificationInboxCategory.RESERVATION, "/my-reservations"));

        saved.markRead(READ_AT);
        testEntityManager.flush();
        testEntityManager.clear();

        NotificationInbox found = testEntityManager.find(NotificationInbox.class, saved.getId());
        found.markRead(READ_AT.plusHours(1));
        testEntityManager.flush();

        assertThat(found.getReadAt()).isEqualTo(READ_AT);
    }

    private NotificationInbox inbox(NotificationInboxCategory category, String linkPath) {
        return NotificationInbox.builder()
                .userId(USER_ID)
                .category(category)
                .title("예약이 확정되었습니다")
                .body("[땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 확정되었습니다.")
                .linkPath(linkPath)
                .build();
    }
}
