package com.ucamp.app.domain.shop;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;


import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.domain.shop.dto.request.ShopUpdateRequest;
import com.ucamp.app.domain.shop.service.ShopService;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ShopService.class, TimeConfig.class})
@ActiveProfiles("test")
class ShopPersistenceTest {

    @Autowired
    private ShopService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("기존 스키마에서 매장 정보와 좌표 변경이 실제 저장된다")
    void update_existingShop_persistsProfile() {
        jdbc.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (?, ?)", 9001L, "테스트 카테고리");
        // 실제 인증 정보가 아닌 테스트 전용 더미 계정이다.
        jdbc.update("""
                INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE,
                    ADDRESS, LATITUDE, LONGITUDE, PHONE, STATUS, CREATED_AT, UPDATED_AT)
                VALUES (9001, 9001, 'CHANGE_ME', 'CHANGE_ME', '기존 매장', '11680',
                    '서울', 37.5, 127.0, '02-000-0000', 'ACTIVE', ?, ?)
                """, LocalDateTime.of(2026, 10, 23, 12, 0), LocalDateTime.of(2026, 10, 23, 12, 0));
        jdbc.update("UPDATE SHOP SET IMAGE_URL = '/existing-image.png' WHERE SHOP_ID = 9001");
        AuthUser owner = new AuthUser(9001L, UserRole.OWNER, 9001L);
        ShopUpdateRequest request = new ShopUpdateRequest(9001L, "변경 매장", "11680", "변경 주소", "2층",
                new BigDecimal("37.1234567"), new BigDecimal("127.1234567"), "02-000-0000",
                "소개", "공지", null);

        service.updateShop(owner, 9001L, request);
        entityManager.flush();
        entityManager.clear();

        var result = service.getShop(owner, 9001L);
        assertThat(result.name()).isEqualTo("변경 매장");
        assertThat(result.address()).isEqualTo("변경 주소");
        assertThat(result.latitude()).isEqualByComparingTo("37.1234567");
        assertThat(result.longitude()).isEqualByComparingTo("127.1234567");
        assertThat(result.notice()).isEqualTo("공지");
        assertThat(result.imageUrl()).isEqualTo("/existing-image.png");
        assertThat(jdbc.queryForObject("SELECT LOGIN_ID FROM SHOP WHERE SHOP_ID = 9001", String.class))
                .isEqualTo("CHANGE_ME");
        assertThat(jdbc.queryForObject("SELECT PASSWORD_HASH FROM SHOP WHERE SHOP_ID = 9001", String.class))
                .isEqualTo("CHANGE_ME");
        assertThat(jdbc.queryForObject("SELECT STATUS FROM SHOP WHERE SHOP_ID = 9001", String.class))
                .isEqualTo("ACTIVE");
    }
}
