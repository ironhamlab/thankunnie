package com.ucamp.app.domain.explore.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

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
import org.springframework.test.context.TestPropertySource;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancyFilter;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancySearchRequest;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancySort;
import com.ucamp.app.domain.explore.dto.response.ExploreVacancyResponse;
import com.ucamp.app.domain.vacancy.policy.PriceTier;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

// 임박 빈자리의 8시간 범위가 자정을 넘는 경우. 지금은 2026-11-06(금) 22:00 이라 범위는 다음 날 06:00 전까지다.
// 초기 데이터의 매장 1(금 10~20시, 20~22시 열기)·매장 2(금 11~21시)는 오늘 남은 시각이 없다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ExploreVacancyService.class, AvailabilityService.class, ExploreImminentMidnightTest.FixedClock.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class ExploreImminentMidnightTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 22, 0);

    @Autowired
    private ExploreVacancyService exploreVacancyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("8시간 범위가 자정을 넘으면 다음 날 새벽 빈 시각도 낸다")
    void searchImminent_rangeCrossesMidnight_includesNextDayEarlyMorning() {
        openShopTwo(NOW.plusHours(3), NOW.plusHours(5));

        // 매장 2 다음 날 01:00: 디지털 펌(할인 있음)이 남성 커트(할인 없음)보다 앞선다
        assertThat(exploreVacancyService.searchImminent()).extracting(ExploreVacancyResponse::vacancyId)
                .containsExactly("4-202611070100");
    }

    @Test
    @DisplayName("타임세일도 날짜를 생략하면 자정을 넘은 3시간 이내 새벽 빈 시각을 낸다")
    void search_lastMinuteTier_rangeCrossesMidnight_includesNextDayEarlyMorning() {
        openShopTwo(NOW.plusHours(3), NOW.plusHours(5));
        ExploreVacancyFilter filter = new ExploreVacancyFilter(null, null, null, null, null, null, null, null,
                PriceTier.LAST_MINUTE, null, null, null, null, null);

        // 정확히 3시간 전인 01:00 은 초임박 구간이다. 01:30 은 3시간을 넘어 빠진다
        assertThat(exploreVacancyService.search(new ExploreVacancySearchRequest(filter, ExploreVacancySort.START_ASC,
                0, 20)).content()).extracting(ExploreVacancyResponse::vacancyId).containsExactly("4-202611070100");
    }

    @Test
    @DisplayName("다음 날이라도 8시간이 지난 시각은 내지 않는다")
    void searchImminent_nextDayAfterEightHours_excluded() {
        openShopTwo(NOW.plusHours(8), NOW.plusHours(10));

        assertThat(exploreVacancyService.searchImminent()).isEmpty();
    }

    private void openShopTwo(LocalDateTime startsAt, LocalDateTime endsAt) {
        jdbcTemplate.update("""
                INSERT INTO SHOP_SCHEDULE_EXCEPTION (EXCEPTION_ID, SHOP_ID, RESERVATION_ID, EXCEPTION_TYPE,
                                                     STARTS_AT, ENDS_AT, REASON, CREATED_AT, UPDATED_AT)
                VALUES (900, 2, NULL, 'OPEN', ?, ?, '새벽 영업', ?, ?)
                """, startsAt, endsAt, NOW, NOW);
    }

    /** 현재 시각과 저장 시각을 NOW 로 고정한다. */
    @TestConfiguration
    @EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);
        }

        @Bean
        DateTimeProvider auditingDateTimeProvider(Clock clock) {
            return () -> Optional.of(LocalDateTime.now(clock));
        }
    }
}
