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
@Import(ShopCategoryRegionRepository.class)
@ActiveProfiles("test")
class ShopCategoryRegionRepositoryTest {

    @Autowired
    private ShopCategoryRegionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        NotificationFixture.category(jdbcTemplate, 10L, "NAIL");
        NotificationFixture.category(jdbcTemplate, 20L, "HAIR");
        NotificationFixture.shop(jdbcTemplate, 1L, 10L, "SEOUL_GANGNAM", "ACTIVE");
        NotificationFixture.shop(jdbcTemplate, 2L, 20L, "SEOUL_MAPO", "INACTIVE");
    }

    @Test
    @DisplayName("매장이 있으면 그 매장의 카테고리 ID와 지역코드를 읽는다")
    void findByShopId_existingShop_returnsCategoryAndRegion() {
        ShopCategoryRegion result = repository.findByShopId(1L).orElseThrow();

        assertThat(result.categoryId()).isEqualTo(10L);
        assertThat(result.regionCode()).isEqualTo("SEOUL_GANGNAM");
    }

    @Test
    @DisplayName("다른 매장은 그 매장 값을 읽고, 비활성 매장도 카테고리와 지역은 읽는다")
    void findByShopId_otherInactiveShop_returnsThatShopsValues() {
        ShopCategoryRegion result = repository.findByShopId(2L).orElseThrow();

        assertThat(result.categoryId()).isEqualTo(20L);
        assertThat(result.regionCode()).isEqualTo("SEOUL_MAPO");
    }

    @Test
    @DisplayName("없는 매장과 매장 ID가 없으면 빈 결과를 반환한다")
    void findByShopId_missingShop_returnsEmpty() {
        assertThat(repository.findByShopId(999L)).isEmpty();
        assertThat(repository.findByShopId(null)).isEmpty();
    }
}
