package com.ucamp.app.domain.explore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.explore.dto.response.ExploreNotBookableReason;
import com.ucamp.app.domain.explore.dto.response.ExploreVacancyDetailResponse;
import com.ucamp.app.domain.vacancy.policy.PriceTier;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

// 초기 데이터: 매장 1(강남구, 금 영업 10~20시)이 2026-11-06(금) 20~22시를 열었다. 시술 1 젤네일 60분, 시술 2 손 케어 30분.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ExploreVacancyDetailService.class, AvailabilityService.class,
        ExploreVacancyDetailServiceTest.FixedClock.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class ExploreVacancyDetailServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Autowired
    private ExploreVacancyDetailService exploreVacancyDetailService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("열린 시간대 안 시각은 예약 가능이고, 목록 필드에 시술·매장 안내와 잔금을 더해 돌려준다")
    void getDetail_openSlot_bookableWithDetails() {
        ExploreVacancyDetailResponse detail = exploreVacancyDetailService.getDetail(1L, NOW.withHour(20));

        assertThat(detail.vacancyId()).isEqualTo("1-202611062000");
        assertThat(detail.bookable()).isTrue();
        assertThat(detail.notBookableReason()).isNull();
        assertThat(detail.shopName()).isEqualTo("언니네 네일");
        assertThat(detail.categoryName()).isEqualTo("네일");
        assertThat(detail.treatmentType()).isEqualTo("NORMAL");
        assertThat(detail.startsAt()).isEqualTo(OffsetDateTime.of(2026, 11, 6, 20, 0, 0, 0, KST));
        assertThat(detail.endsAt()).isEqualTo(OffsetDateTime.of(2026, 11, 6, 21, 0, 0, 0, KST));
        assertThat(detail.bookingClosesAt()).isEqualTo(OffsetDateTime.of(2026, 11, 6, 19, 40, 0, 0, KST));
        assertThat(detail.priceTier()).isEqualTo(PriceTier.NORMAL);
        assertThat(detail.regularPrice()).isEqualTo(35000);
        assertThat(detail.salePrice()).isEqualTo(30000);
        assertThat(detail.discountRate()).isEqualTo(14);
        assertThat(detail.depositAmount()).isEqualTo(20000);
        assertThat(detail.balanceAmount()).isEqualTo(10000);
        assertThat(detail.treatmentDescription()).isEqualTo("기본 원컬러 젤네일");
        assertThat(detail.addressDetail()).isEqualTo("2층");
        assertThat(detail.phone()).isEqualTo("02-111-1111");
        assertThat(detail.shopDescription()).isEqualTo("젤네일·케어 전문");
        assertThat(detail.shopNotice()).isEqualTo("예약 시간 10분 전 도착해 주세요");
    }

    @Test
    @DisplayName("클릭 마감(시작 20분 전)이 지난 시각은 200 으로 BOOKING_CLOSED 를 알리고 가격은 그 시점 구간으로 계산한다")
    void getDetail_afterDeadline_bookingClosed() {
        ExploreVacancyDetailResponse detail = exploreVacancyDetailService.getDetail(1L, NOW.withMinute(0));

        assertThat(detail.bookable()).isFalse();
        assertThat(detail.notBookableReason()).isEqualTo(ExploreNotBookableReason.BOOKING_CLOSED);
        assertThat(detail.priceTier()).isEqualTo(PriceTier.LAST_MINUTE);
        assertThat(detail.salePrice()).isEqualTo(27000);
    }

    @Test
    @DisplayName("열린 시간을 넘기거나 다른 예약과 겹치는 시각은 NOT_AVAILABLE 이다")
    void getDetail_notFree_notAvailable() {
        // 젤네일 60분은 21:30 에 시작하면 열린 시간(22시)을 넘긴다
        assertThat(exploreVacancyDetailService.getDetail(1L, NOW.withHour(21).withMinute(30)).notBookableReason())
                .isEqualTo(ExploreNotBookableReason.NOT_AVAILABLE);

        jdbcTemplate.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                                         APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (900, 1, 1, 'CONFIRMED', ?, ?, 30000, 20000, ?, ?, ?)
                """, NOW.withHour(20), NOW.withHour(21), NOW, NOW, NOW);

        ExploreVacancyDetailResponse taken = exploreVacancyDetailService.getDetail(2L, NOW.withHour(20).withMinute(30));
        assertThat(taken.bookable()).isFalse();
        assertThat(taken.notBookableReason()).isEqualTo(ExploreNotBookableReason.NOT_AVAILABLE);
    }

    @Test
    @DisplayName("시술 이미지가 없으면 매장 이미지를 쓴다")
    void getDetail_noTreatmentImage_usesShopImage() {
        jdbcTemplate.update("UPDATE SHOP SET IMAGE_URL = '/shop.jpg' WHERE SHOP_ID = 1");

        assertThat(exploreVacancyDetailService.getDetail(1L, NOW.withHour(20)).imageUrl()).isEqualTo("/shop.jpg");
    }

    @Test
    @DisplayName("30분 단위가 아니면 VACANCY_004, 오늘부터 60일 범위 밖이면 VACANCY_003 이다")
    void getDetail_invalidStartsAt_throws() {
        assertError(1L, NOW.withHour(20).withMinute(15), ErrorCode.VACANCY_INVALID_TIME_UNIT);
        assertError(1L, NOW.withHour(20).withSecond(30), ErrorCode.VACANCY_INVALID_TIME_UNIT);
        assertError(1L, NOW.minusDays(1).withHour(20), ErrorCode.VACANCY_OUT_OF_RANGE);
        assertError(1L, NOW.plusDays(60).withHour(20), ErrorCode.VACANCY_OUT_OF_RANGE);

        assertThat(exploreVacancyDetailService.getDetail(1L, NOW.plusDays(59).withHour(12)).vacancyId())
                .isEqualTo("1-202701041200");
    }

    @Test
    @DisplayName("시술이 없거나 판매 중지, 매장이 노출 중지면 TREATMENT_001 이다")
    void getDetail_missingOrInactive_throwsTreatmentNotFound() {
        assertError(999L, NOW.withHour(20), ErrorCode.TREATMENT_NOT_FOUND);

        jdbcTemplate.update("UPDATE TREATMENT SET STATUS = 'INACTIVE' WHERE TREATMENT_ID = 2");
        assertError(2L, NOW.withHour(20), ErrorCode.TREATMENT_NOT_FOUND);

        jdbcTemplate.update("UPDATE SHOP SET STATUS = 'INACTIVE' WHERE SHOP_ID = 1");
        assertError(1L, NOW.withHour(20), ErrorCode.TREATMENT_NOT_FOUND);
    }

    private void assertError(Long treatmentId, LocalDateTime startsAt, ErrorCode errorCode) {
        assertThatThrownBy(() -> exploreVacancyDetailService.getDetail(treatmentId, startsAt))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

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
