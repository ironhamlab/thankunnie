package com.ucamp.app.domain.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.schedule.entity.BusinessDay;
import com.ucamp.app.domain.schedule.entity.ShopBusinessHour;

// 스키마는 docs/erd/01_schema_ddl.sql 로 만든 test 프로필 H2(MODE=Oracle)를 그대로 쓴다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig.class)
@ActiveProfiles("test")
class ShopBusinessHourRepositoryTest {

    private static final long SHOP_ID = 1L;
    private static final Timestamp FIXED = Timestamp.valueOf(LocalDateTime.of(2026, 10, 22, 9, 0));

    @Autowired
    private ShopBusinessHourRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (1, '네일')");
        jdbcTemplate.update("""
                INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE, ADDRESS,
                    LATITUDE, LONGITUDE, PHONE, CREATED_AT, UPDATED_AT)
                VALUES (?, 1, 'owner1', 'hash', '매장', 'SEOUL', '주소', 37.5, 127.0, '010', ?, ?)
                """, SHOP_ID, FIXED, FIXED);
    }

    @Test
    @DisplayName("영업일은 HH:MM 과 'N', 휴무일은 NULL 과 'Y' 로 저장하고 다시 읽는다")
    void save_convertsTimeAndClosedFlag() {
        repository.save(ShopBusinessHour.builder().shopId(SHOP_ID).dayOfWeek(BusinessDay.MON)
                .closed(false).openTime(LocalTime.of(10, 0)).closeTime(LocalTime.of(20, 30)).build());
        repository.saveAndFlush(ShopBusinessHour.builder().shopId(SHOP_ID).dayOfWeek(BusinessDay.SUN)
                .closed(true).openTime(LocalTime.of(10, 0)).closeTime(LocalTime.of(20, 0)).build());

        Map<String, Object> monday = jdbcTemplate.queryForMap(
                "SELECT OPEN_TIME, CLOSE_TIME, IS_CLOSED FROM SHOP_BUSINESS_HOUR WHERE DAY_OF_WEEK = 'MON'");
        Map<String, Object> sunday = jdbcTemplate.queryForMap(
                "SELECT OPEN_TIME, IS_CLOSED FROM SHOP_BUSINESS_HOUR WHERE DAY_OF_WEEK = 'SUN'");
        List<ShopBusinessHour> loaded = repository.findByShopId(SHOP_ID);

        assertThat(monday).containsEntry("OPEN_TIME", "10:00").containsEntry("CLOSE_TIME", "20:30")
                .containsEntry("IS_CLOSED", "N");
        assertThat(sunday.get("OPEN_TIME")).isNull();
        assertThat(sunday).containsEntry("IS_CLOSED", "Y");
        assertThat(loaded).hasSize(2)
                .anySatisfy(hour -> assertThat(hour.getCloseTime()).isEqualTo(LocalTime.of(20, 30)));
    }
}
