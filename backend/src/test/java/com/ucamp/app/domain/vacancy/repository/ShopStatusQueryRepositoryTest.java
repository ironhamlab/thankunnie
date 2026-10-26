package com.ucamp.app.domain.vacancy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ucamp.app.config.TimeConfig;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ShopStatusQueryRepository.class, TimeConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class ShopStatusQueryRepositoryTest {

    @Autowired
    private ShopStatusQueryRepository shopStatusQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("운영 중인 매장만 활성으로 본다")
    void isActive_byShopStatus() {
        jdbcTemplate.update("UPDATE SHOP SET STATUS = 'INACTIVE' WHERE SHOP_ID = 2");

        assertThat(shopStatusQueryRepository.isActive(1L)).isTrue();
        assertThat(shopStatusQueryRepository.isActive(2L)).isFalse();
        assertThat(shopStatusQueryRepository.isActive(999L)).isFalse();
    }
}
