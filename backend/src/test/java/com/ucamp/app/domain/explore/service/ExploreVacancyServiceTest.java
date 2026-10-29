package com.ucamp.app.domain.explore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.explore.dto.request.ExploreTimeOfDay;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancyFilter;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancySearchRequest;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancySort;
import com.ucamp.app.domain.explore.dto.response.ExploreShopMapResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreShopResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreVacancyResponse;
import com.ucamp.app.domain.vacancy.policy.PriceTier;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

// 초기 데이터: 매장 1(강남구, 영업 10~20시)이 2026-11-06(금) 20~22시를 열었다. 시술 1 젤네일 60분, 시술 2 손 케어 30분.
// 열린 시간 안 시작 시각은 젤네일 20:00·20:30·21:00, 손 케어 20:00·20:30·21:00·21:30 으로 모두 7건이다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ExploreVacancyService.class, AvailabilityService.class,
        ExploreVacancyServiceTest.FixedClock.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class ExploreVacancyServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);
    private static final LocalDate OPEN_DAY = LocalDate.of(2026, 11, 6);

    @Autowired
    private ExploreVacancyService exploreVacancyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("열린 시간대 안에서 시작하는 시술 × 시각만 빠른 시작순으로 나오고 영업시간 안 시각은 빠진다")
    void search_openDay_returnsOnlyOpenSlotsInStartOrder() {
        PageResponse<ExploreVacancyResponse> result = exploreVacancyService.search(request().build());

        assertThat(result.totalElements()).isEqualTo(7);
        assertThat(result.content()).extracting(ExploreVacancyResponse::vacancyId).containsExactly(
                "1-202611062000", "2-202611062000", "1-202611062030", "2-202611062030",
                "1-202611062100", "2-202611062100", "2-202611062130");
    }

    @Test
    @DisplayName("응답에 매장 좌표·시각(+09:00)·조회 시점 가격이 담긴다")
    void search_firstItem_hasShopLocationTimesAndPrice() {
        ExploreVacancyResponse first = exploreVacancyService.search(request().build()).content().get(0);

        assertThat(first.shopName()).isEqualTo("언니네 네일");
        assertThat(first.regionCode()).isEqualTo("11680");
        assertThat(first.latitude()).isEqualByComparingTo("37.4979");
        assertThat(first.longitude()).isEqualByComparingTo("127.0276");
        assertThat(first.categoryName()).isEqualTo("네일");
        assertThat(first.startsAt()).isEqualTo(kst(2026, 11, 6, 20, 0));
        assertThat(first.endsAt()).isEqualTo(kst(2026, 11, 6, 21, 0));
        assertThat(first.bookingClosesAt()).isEqualTo(kst(2026, 11, 6, 19, 40));
        // 10시간 남아 일반 구간: 정가 35,000 → 판매가 30,000, 할인율 14%(버림), 예약금 20,000
        assertThat(first.priceTier()).isEqualTo(PriceTier.NORMAL);
        assertThat(first.regularPrice()).isEqualTo(35000);
        assertThat(first.salePrice()).isEqualTo(30000);
        assertThat(first.discountRate()).isEqualTo(14);
        assertThat(first.depositAmount()).isEqualTo(20000);
    }

    @Test
    @DisplayName("날짜를 생략하면 오늘부터 7일을 조회한다")
    void search_noDates_searchesSevenDaysFromToday() {
        ExploreVacancySearchRequest request = new RequestBuilder().build();

        assertThat(exploreVacancyService.search(request).totalElements()).isEqualTo(7);
    }

    @Test
    @DisplayName("매장을 지정하면 그 매장 빈자리만 나온다")
    void search_shopId_returnsOnlyThatShop() {
        openShopTwo();

        assertThat(exploreVacancyService.search(request().shopId(2L).build()).content())
                .extracting(ExploreVacancyResponse::vacancyId)
                .containsExactly("3-202611062100", "3-202611062130");
        assertThat(exploreVacancyService.search(request().shopId(1L).build()).totalElements()).isEqualTo(7);
    }

    @Test
    @DisplayName("지도 매장 요약은 매장마다 한 건이며 최저가·최대 할인율·빈자리 수·가장 빠른 시작을 담는다")
    void searchShops_groupsVacanciesByShop() {
        openShopTwo();

        ExploreShopMapResponse result = exploreVacancyService.searchShops(request().build().filter());

        assertThat(result.hasMore()).isFalse();
        assertThat(result.shops()).extracting(ExploreShopResponse::shopId).containsExactly(1L, 2L);
        ExploreShopResponse shopOne = result.shops().get(0);
        // 젤네일 30,000(14%) 3건 + 손 케어 18,000(10%) 4건
        assertThat(shopOne.vacancyCount()).isEqualTo(7);
        assertThat(shopOne.minSalePrice()).isEqualTo(18000);
        assertThat(shopOne.maxDiscountRate()).isEqualTo(14);
        assertThat(shopOne.earliestStartsAt()).isEqualTo(kst(2026, 11, 6, 20, 0));
        assertThat(shopOne.latitude()).isEqualByComparingTo("37.4979");
        // 남성 커트 25,000(할인 없음) 21:00·21:30. 디지털 펌 120분은 22시 전에 끝나지 않아 빠진다
        ExploreShopResponse shopTwo = result.shops().get(1);
        assertThat(shopTwo.vacancyCount()).isEqualTo(2);
        assertThat(shopTwo.minSalePrice()).isEqualTo(25000);
        assertThat(shopTwo.maxDiscountRate()).isZero();
        assertThat(shopTwo.earliestStartsAt()).isEqualTo(kst(2026, 11, 6, 21, 0));
    }

    @Test
    @DisplayName("지도 매장 요약도 지도 범위·필터를 적용하고, 빈자리가 없는 매장은 빠진다")
    void searchShops_appliesFilters() {
        openShopTwo();

        assertThat(exploreVacancyService.searchShops(request()
                .bounds("37.49", "37.50", "127.02", "127.03").build().filter()).shops())
                .extracting(ExploreShopResponse::shopId).containsExactly(1L);
        assertThat(exploreVacancyService.searchShops(request().categoryId(2L).build().filter()).shops())
                .extracting(ExploreShopResponse::shopId).containsExactly(2L);
        assertThat(exploreVacancyService.searchShops(request().timeOfDay(ExploreTimeOfDay.MORNING).build().filter())
                .shops()).isEmpty();
    }

    @Test
    @DisplayName("다른 예약이 잡은 시간과 겹치는 시각은 빠진다")
    void search_reservedTime_excludesOverlappingStarts() {
        jdbcTemplate.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                                         APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (900, 1, 1, 'CONFIRMED', ?, ?, 30000, 20000, ?, ?, ?)
                """, NOW.withHour(20), NOW.withHour(21), NOW, NOW, NOW);

        List<String> ids = exploreVacancyService.search(request().build()).content().stream()
                .map(ExploreVacancyResponse::vacancyId).toList();

        assertThat(ids).containsExactly("1-202611062100", "2-202611062100", "2-202611062130");
    }

    @Test
    @DisplayName("지역·카테고리·검색어·지도 범위 조건이 매장과 시술에 적용된다")
    void search_shopAndTreatmentFilters_applied() {
        assertThat(exploreVacancyService.search(request().regionCodes(List.of("11680")).build())
                .totalElements()).isEqualTo(7);
        assertThat(exploreVacancyService.search(request().regionCodes(List.of("11650")).build())
                .totalElements()).isZero();
        assertThat(exploreVacancyService.search(request().categoryId(2L).build()).totalElements()).isZero();
        assertThat(exploreVacancyService.search(request().keyword("케어").build()).content())
                .allMatch(item -> item.treatmentId() == 2L).hasSize(4);
        assertThat(exploreVacancyService.search(request()
                .bounds("37.49", "37.50", "127.02", "127.03").build()).totalElements()).isEqualTo(7);
        assertThat(exploreVacancyService.search(request()
                .bounds("37.50", "37.60", "127.02", "127.03").build()).totalElements()).isZero();
    }

    @Test
    @DisplayName("검색어의 % 는 와일드카드가 아닌 글자로 찾는다")
    void search_keywordWithPercent_matchesLiterally() {
        assertThat(exploreVacancyService.search(request().keyword("%").build()).totalElements()).isZero();
    }

    @Test
    @DisplayName("판매 중지한 시술과 노출 중지한 매장은 빠진다")
    void search_inactiveTreatmentOrShop_excluded() {
        jdbcTemplate.update("UPDATE TREATMENT SET STATUS = 'INACTIVE' WHERE TREATMENT_ID = 2");
        assertThat(exploreVacancyService.search(request().build()).content())
                .allMatch(item -> item.treatmentId() == 1L).hasSize(3);

        jdbcTemplate.update("UPDATE SHOP SET STATUS = 'INACTIVE' WHERE SHOP_ID = 1");
        assertThat(exploreVacancyService.search(request().build()).totalElements()).isZero();
    }

    @Test
    @DisplayName("시간대·마감 임박·가격 구간 조건은 시작 시각 기준으로 거른다")
    void search_timeFilters_applied() {
        assertThat(exploreVacancyService.search(request().timeOfDay(ExploreTimeOfDay.EVENING).build())
                .totalElements()).isEqualTo(7);
        assertThat(exploreVacancyService.search(request().timeOfDay(ExploreTimeOfDay.MORNING).build())
                .totalElements()).isZero();
        // 10시간 안에 마감(19:40 이하)되는 것은 20:00 시작 두 건뿐이다
        assertThat(exploreVacancyService.search(request().closingWithinHours(10).build()).content())
                .extracting(ExploreVacancyResponse::vacancyId)
                .containsExactly("1-202611062000", "2-202611062000");
        // 지금은 모두 8시간보다 많이 남아 일반 구간이다
        assertThat(exploreVacancyService.search(request().priceTier(PriceTier.IMMINENT).build())
                .totalElements()).isZero();
    }

    @Test
    @DisplayName("판매가 낮은 순으로 정렬하면 같은 가격은 시작 시각 순이다")
    void search_priceAsc_sortsByPriceThenStart() {
        List<String> ids = exploreVacancyService.search(request().sort(ExploreVacancySort.PRICE_ASC).build())
                .content().stream().map(ExploreVacancyResponse::vacancyId).toList();

        assertThat(ids).containsExactly(
                "2-202611062000", "2-202611062030", "2-202611062100", "2-202611062130",
                "1-202611062000", "1-202611062030", "1-202611062100");
    }

    @Test
    @DisplayName("페이지를 나누면 전체 개수는 유지하고 해당 페이지만 돌려준다")
    void search_paging_returnsRequestedPage() {
        PageResponse<ExploreVacancyResponse> page = exploreVacancyService.search(request().page(1, 3).build());

        assertThat(page.content()).extracting(ExploreVacancyResponse::vacancyId)
                .containsExactly("2-202611062030", "1-202611062100", "2-202611062100");
        assertThat(page.totalElements()).isEqualTo(7);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    @DisplayName("이미지는 시술 이미지, 없으면 매장 이미지를 쓴다")
    void search_image_fallsBackToShopImage() {
        jdbcTemplate.update("UPDATE SHOP SET IMAGE_URL = '/shop.jpg' WHERE SHOP_ID = 1");
        jdbcTemplate.update("UPDATE TREATMENT SET IMAGE_URL = '/care.jpg' WHERE TREATMENT_ID = 2");

        List<ExploreVacancyResponse> content = exploreVacancyService.search(request().build()).content();

        assertThat(content.get(0).imageUrl()).isEqualTo("/shop.jpg");
        assertThat(content.get(1).imageUrl()).isEqualTo("/care.jpg");
    }

    @Test
    @DisplayName("날짜 순서·60일 범위·페이지 크기·지도 범위가 잘못되면 400 이다")
    void search_invalidRequest_throwsInvalidInput() {
        assertInvalid(request().dates(OPEN_DAY, OPEN_DAY.minusDays(1)).build());
        assertInvalid(request().dates(OPEN_DAY, NOW.toLocalDate().plusDays(60)).build());
        assertInvalid(request().page(0, 51).build());
        assertInvalid(request().page(-1, 20).build());
        assertInvalid(request().closingWithinHours(0).build());
        assertInvalid(new RequestBuilder().bounds("37.49", null, "127.02", "127.03").build());
        assertInvalid(request().bounds("37.50", "37.49", "127.02", "127.03").build());
    }

    // 매장 2(서초구, 금 11~21시)가 2026-11-06 21~22시를 연다
    private void openShopTwo() {
        jdbcTemplate.update("""
                INSERT INTO SHOP_SCHEDULE_EXCEPTION (EXCEPTION_ID, SHOP_ID, RESERVATION_ID, EXCEPTION_TYPE,
                                                     STARTS_AT, ENDS_AT, REASON, CREATED_AT, UPDATED_AT)
                VALUES (900, 2, NULL, 'OPEN', ?, ?, '연장', ?, ?)
                """, NOW.withHour(21), NOW.withHour(22), NOW, NOW);
    }

    private void assertInvalid(ExploreVacancySearchRequest request) {
        assertThatThrownBy(() -> exploreVacancyService.search(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private static RequestBuilder request() {
        return new RequestBuilder().dates(OPEN_DAY, OPEN_DAY);
    }

    private static OffsetDateTime kst(int year, int month, int day, int hour, int minute) {
        return OffsetDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.ofHours(9));
    }

    private static final class RequestBuilder {

        private LocalDate startDate;
        private LocalDate endDate;
        private List<String> regionCodes;
        private Long categoryId;
        private Long shopId;
        private ExploreTimeOfDay timeOfDay;
        private Integer closingWithinHours;
        private PriceTier priceTier;
        private String keyword;
        private BigDecimal minLat;
        private BigDecimal maxLat;
        private BigDecimal minLng;
        private BigDecimal maxLng;
        private ExploreVacancySort sort = ExploreVacancySort.START_ASC;
        private int page;
        private int size = 20;

        RequestBuilder dates(LocalDate start, LocalDate end) {
            this.startDate = start;
            this.endDate = end;
            return this;
        }

        RequestBuilder regionCodes(List<String> codes) {
            this.regionCodes = codes;
            return this;
        }

        RequestBuilder categoryId(Long id) {
            this.categoryId = id;
            return this;
        }

        RequestBuilder shopId(Long id) {
            this.shopId = id;
            return this;
        }

        RequestBuilder timeOfDay(ExploreTimeOfDay value) {
            this.timeOfDay = value;
            return this;
        }

        RequestBuilder closingWithinHours(Integer hours) {
            this.closingWithinHours = hours;
            return this;
        }

        RequestBuilder priceTier(PriceTier tier) {
            this.priceTier = tier;
            return this;
        }

        RequestBuilder keyword(String value) {
            this.keyword = value;
            return this;
        }

        RequestBuilder bounds(String minLatValue, String maxLatValue, String minLngValue, String maxLngValue) {
            this.minLat = decimal(minLatValue);
            this.maxLat = decimal(maxLatValue);
            this.minLng = decimal(minLngValue);
            this.maxLng = decimal(maxLngValue);
            return this;
        }

        RequestBuilder sort(ExploreVacancySort value) {
            this.sort = value;
            return this;
        }

        RequestBuilder page(int pageValue, int sizeValue) {
            this.page = pageValue;
            this.size = sizeValue;
            return this;
        }

        ExploreVacancySearchRequest build() {
            ExploreVacancyFilter filter = new ExploreVacancyFilter(startDate, endDate, regionCodes, categoryId,
                    shopId, timeOfDay, closingWithinHours, priceTier, keyword, minLat, maxLat, minLng, maxLng);
            return new ExploreVacancySearchRequest(filter, sort, page, size);
        }

        private static BigDecimal decimal(String value) {
            return value == null ? null : new BigDecimal(value);
        }
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
