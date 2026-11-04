package com.ucamp.app.domain.vacancy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
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
import com.ucamp.app.domain.explore.service.ExploreVacancyService;
import com.ucamp.app.domain.vacancy.service.AvailabilityService.AvailabilitySnapshot;

// 초기 데이터의 매장 1·2 에 열기·닫기·예약을 넣고, 묶어 읽은 결과가 한 매장씩 읽은 결과와 같은지 본다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AvailabilityService.class, ExploreVacancyService.class, AvailabilitySnapshotTest.FixedClock.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.sql.init.data-locations=classpath:init-data.sql",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
class AvailabilitySnapshotTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);
    private static final LocalDate FIRST_DAY = NOW.toLocalDate();
    private static final LocalDate LAST_DAY = FIRST_DAY.plusDays(4);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 7);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);
    private static final List<Long> SHOP_IDS = List.of(1L, 2L);
    private static final int[] DURATIONS = {30, 60, 90, 180};
    private static final int MAX_DURATION = 180;

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private ExploreVacancyService exploreVacancyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        insertException(901, 2, "OPEN", SATURDAY.atTime(22, 0), SUNDAY.atTime(3, 0));
        insertException(902, 1, "CLOSE", SATURDAY.atTime(12, 0), SATURDAY.atTime(14, 0));
        insertReservation(901, 1, "CONFIRMED", SATURDAY.atTime(15, 0), NOW);
        insertReservation(902, 3, "HOLD", SATURDAY.atTime(13, 0), NOW.plusMinutes(5));
        insertReservation(903, 1, "HOLD", SUNDAY.atTime(11, 0), NOW.minusMinutes(1));
        insertReservation(904, 1, "CANCELLED", SUNDAY.atTime(15, 0), NOW);
        insertReservation(905, 3, "CANCEL_PENDING", SUNDAY.atTime(13, 0), NOW);
    }

    @Test
    @DisplayName("묶어 읽은 시작 시각은 매장·날짜·소요 시간 조합마다 한 매장씩 읽은 결과와 같다")
    void startTimes_sameAsSingleShopCalculation() {
        AvailabilitySnapshot snapshot = availabilityService.loadSnapshot(SHOP_IDS, FIRST_DAY, LAST_DAY, MAX_DURATION);

        List<LocalDateTime> all = new ArrayList<>();
        for (Long shopId : SHOP_IDS) {
            for (LocalDate date = FIRST_DAY; !date.isAfter(LAST_DAY); date = date.plusDays(1)) {
                for (int duration : DURATIONS) {
                    List<LocalDateTime> expected = availabilityService.getAvailableStartTimes(shopId, date, duration);
                    assertThat(snapshot.startTimes(shopId, date, duration))
                            .as("매장 %d, %s, %d분", shopId, date, duration)
                            .isEqualTo(expected);
                    all.addAll(expected);
                }
            }
        }
        assertThat(all).isNotEmpty();
    }

    @Test
    @DisplayName("닫기·확정 예약·유효한 점유는 막고, 자정을 넘긴 열기 안의 시각은 낸다")
    void startTimes_appliesBlocksAndMidnightOpen() {
        AvailabilitySnapshot snapshot = availabilityService.loadSnapshot(SHOP_IDS, FIRST_DAY, LAST_DAY, MAX_DURATION);

        assertThat(snapshot.startTimes(1L, SATURDAY, 60))
                .doesNotContain(SATURDAY.atTime(12, 0), SATURDAY.atTime(15, 0));
        assertThat(snapshot.startTimes(2L, SATURDAY, 30))
                .doesNotContain(SATURDAY.atTime(13, 0))
                .contains(SATURDAY.atTime(23, 30));
        assertThat(snapshot.startTimes(2L, SUNDAY, 30)).contains(SUNDAY.atTime(2, 30));
    }

    @Test
    @DisplayName("없는 매장은 시작 시각이 없고, 매장 목록이 비어도 쿼리 오류가 나지 않는다")
    void startTimes_unknownShopOrEmptyShops_returnsEmpty() {
        assertThat(availabilityService.loadSnapshot(List.of(), FIRST_DAY, LAST_DAY, MAX_DURATION)
                .startTimes(1L, FIRST_DAY, 60)).isEmpty();
        assertThat(availabilityService.loadSnapshot(List.of(999_999L), FIRST_DAY, LAST_DAY, MAX_DURATION)
                .startTimes(999_999L, FIRST_DAY, 60)).isEmpty();
    }

    @Test
    @DisplayName("빈자리 탐색 쿼리 수는 조회 기간이 길어져도 늘어나지 않는다")
    void searchShops_queryCountDoesNotGrowWithRange() {
        long oneDay = queryCount(FIRST_DAY);
        long longRange = queryCount(FIRST_DAY.plusDays(29));

        assertThat(longRange).isEqualTo(oneDay).isLessThanOrEqualTo(4);
    }

    private long queryCount(LocalDate endDate) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        exploreVacancyService.searchShops(new ExploreVacancyFilter(FIRST_DAY, endDate, null, null, null, null, null,
                null, null, null, null, null, null, null));
        return statistics.getPrepareStatementCount();
    }

    private void insertException(long id, long shopId, String type, LocalDateTime startsAt, LocalDateTime endsAt) {
        jdbcTemplate.update("""
                INSERT INTO SHOP_SCHEDULE_EXCEPTION (EXCEPTION_ID, SHOP_ID, RESERVATION_ID, EXCEPTION_TYPE,
                                                     STARTS_AT, ENDS_AT, REASON, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, NULL, ?, ?, ?, '테스트', ?, ?)
                """, id, shopId, type, startsAt, endsAt, NOW, NOW);
    }

    private void insertReservation(long id, long treatmentId, String status, LocalDateTime startsAt,
            LocalDateTime holdExpiresAt) {
        jdbcTemplate.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                                         APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (?, 1, ?, ?, ?, ?, 30000, 20000, ?, ?, ?)
                """, id, treatmentId, status, startsAt, startsAt.plusHours(1), holdExpiresAt, NOW, NOW);
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
