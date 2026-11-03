package com.ucamp.app.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
        assertThat(saved.getShopId()).isNull();
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
    @DisplayName("매장 알림은 매장만 지정하고 회원은 비운다")
    void save_shopInbox_hasShopOnly() {
        NotificationFixture.category(jdbcTemplate, 1L, "네일");
        NotificationFixture.shop(jdbcTemplate, 1L, 1L, "11680", "ACTIVE");

        NotificationInbox saved = testEntityManager.persistAndFlush(NotificationInbox.builder()
                .shopId(1L)
                .category(NotificationInboxCategory.RESERVATION)
                .title("예약이 확정되었어요")
                .body("김고객 11월 2일 10:00 예약이 확정되었습니다.")
                .linkPath("/owner/bookings")
                .build());

        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getShopId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("회원과 매장을 같이 지정하면 만들지 않는다")
    void create_bothRecipients_rejected() {
        assertThatThrownBy(() -> NotificationInbox.builder()
                .userId(USER_ID)
                .shopId(1L)
                .category(NotificationInboxCategory.RESERVATION)
                .title("제목")
                .body("본문")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("회원과 매장을 둘 다 비우면 만들지 않는다")
    void create_noRecipient_rejected() {
        assertThatThrownBy(() -> NotificationInbox.builder()
                .category(NotificationInboxCategory.RESERVATION)
                .title("제목")
                .body("본문")
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("회원과 매장을 같이 넣는 SQL 은 체크 제약으로 거절된다")
    void insert_bothRecipients_rejectedByCheck() {
        NotificationFixture.category(jdbcTemplate, 1L, "네일");
        NotificationFixture.shop(jdbcTemplate, 1L, 1L, "11680", "ACTIVE");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO NOTIFICATION_INBOX (
                    INBOX_ID, USER_ID, SHOP_ID, CATEGORY, TITLE, BODY, CREATED_AT, UPDATED_AT)
                VALUES (900, ?, 1, 'RESERVATION', '제목', '본문', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("회원과 매장을 둘 다 비우는 SQL 은 체크 제약으로 거절된다")
    void insert_noRecipient_rejectedByCheck() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO NOTIFICATION_INBOX (
                    INBOX_ID, USER_ID, SHOP_ID, CATEGORY, TITLE, BODY, CREATED_AT, UPDATED_AT)
                VALUES (901, NULL, NULL, 'RESERVATION', '제목', '본문', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
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
