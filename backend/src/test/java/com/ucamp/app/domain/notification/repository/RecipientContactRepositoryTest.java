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
import com.ucamp.app.domain.notification.repository.RecipientContactRepository.CancelSmsInfo;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RecipientContactRepository.class)
@ActiveProfiles("test")
class RecipientContactRepositoryTest {

    @Autowired
    private RecipientContactRepository recipientContactRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.shop(jdbcTemplate, 1L, 10L, "SEOUL_GANGNAM", "ACTIVE");
        NotificationFixture.user(jdbcTemplate, 1L);
    }

    @Test
    @DisplayName("회원 ID로 전화번호를 읽는다")
    void findPhoneByUserId_existingUser_returnsPhone() {
        assertThat(recipientContactRepository.findPhoneByUserId(1L)).contains("010-1111-1111");
    }

    @Test
    @DisplayName("없는 회원이면 전화번호가 없다")
    void findPhoneByUserId_missingUser_isEmpty() {
        assertThat(recipientContactRepository.findPhoneByUserId(999L)).isEmpty();
    }

    @Test
    @DisplayName("매장 ID로 매장 이름을 읽는다")
    void findShopNameById_existingShop_returnsName() {
        assertThat(recipientContactRepository.findShopNameById(1L)).contains("매장1");
    }

    @Test
    @DisplayName("없는 매장이면 매장 이름이 없다")
    void findShopNameById_missingShop_isEmpty() {
        assertThat(recipientContactRepository.findShopNameById(999L)).isEmpty();
    }

    @Test
    @DisplayName("예약 ID로 취소 문자에 넣을 고객 번호·매장 이름·예약 시작 시각을 읽는다")
    void findCancelSmsInfo_existingReservation_returnsInfo() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 11, 2, 10, 0);
        NotificationFixture.treatment(jdbcTemplate, 20L, 1L, "NORMAL");
        NotificationFixture.reservation(jdbcTemplate, 100L, 1L, 20L, "CANCELLED",
                startsAt, startsAt.plusHours(1), startsAt.minusDays(1));

        assertThat(recipientContactRepository.findCancelSmsInfo(100L))
                .contains(new CancelSmsInfo("010-1111-1111", "매장1", startsAt));
        assertThat(recipientContactRepository.findReservationSmsInfo(100L))
                .contains(new CancelSmsInfo("010-1111-1111", "매장1", startsAt));
    }

    @Test
    @DisplayName("없는 예약이면 취소 문자 정보가 없다")
    void findCancelSmsInfo_missingReservation_isEmpty() {
        assertThat(recipientContactRepository.findCancelSmsInfo(999L)).isEmpty();
    }
}
