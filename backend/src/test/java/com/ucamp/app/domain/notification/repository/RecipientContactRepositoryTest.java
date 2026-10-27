package com.ucamp.app.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
}
