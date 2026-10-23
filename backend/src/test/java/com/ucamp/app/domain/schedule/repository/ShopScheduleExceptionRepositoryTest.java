package com.ucamp.app.domain.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

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
import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;
import com.ucamp.app.domain.schedule.entity.ShopScheduleException;

// 스키마는 docs/erd/01_schema_ddl.sql 로 만든 test 프로필 H2(MODE=Oracle)를 그대로 쓴다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig.class)
@ActiveProfiles("test")
class ShopScheduleExceptionRepositoryTest {

    private static final long SHOP_ID = 1L;
    private static final long OTHER_SHOP_ID = 2L;
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 25, 12, 0);

    @Autowired
    private ShopScheduleExceptionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // SHOP FK 를 맞추기 위한 최소 데이터
        jdbcTemplate.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (1, '네일')");
        for (long shopId : new long[] {SHOP_ID, OTHER_SHOP_ID}) {
            jdbcTemplate.update("""
                    INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE, ADDRESS,
                        LATITUDE, LONGITUDE, PHONE, CREATED_AT, UPDATED_AT)
                    VALUES (?, 1, ?, 'hash', '매장', 'SEOUL', '주소', 37.5, 127.0, '010', TIMESTAMP '2026-10-22 09:00:00', TIMESTAMP '2026-10-22 09:00:00')
                    """, shopId, "owner" + shopId);
        }
    }

    @Test
    @DisplayName("시퀀스로 ID 를 발급받아 저장하고 생성·수정 시각을 기록한다")
    void save_assignsIdAndAuditTime() {
        ShopScheduleException saved = repository.saveAndFlush(close(SHOP_ID, NOON, NOON.plusHours(1)));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("조회 구간과 겹치는 본인 매장 일정만 시작 시각 순으로 조회하고 종료 시각은 포함하지 않는다")
    void findOverlapping_returnsOverlappingInShop() {
        ShopScheduleException lunch = repository.save(close(SHOP_ID, NOON, NOON.plusHours(1)));
        ShopScheduleException morning = repository.save(close(SHOP_ID, NOON.minusHours(3), NOON.minusHours(2)));
        repository.save(close(SHOP_ID, NOON.plusHours(1), NOON.plusHours(2)));
        repository.save(close(OTHER_SHOP_ID, NOON, NOON.plusHours(1)));

        List<ShopScheduleException> result =
                repository.findOverlapping(SHOP_ID, NOON.minusHours(3), NOON.plusHours(1));

        assertThat(result).containsExactly(morning, lunch);
    }

    private static ShopScheduleException close(long shopId, LocalDateTime startsAt, LocalDateTime endsAt) {
        return ShopScheduleException.builder()
                .shopId(shopId)
                .exceptionType(ScheduleExceptionType.CLOSE)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
    }
}
