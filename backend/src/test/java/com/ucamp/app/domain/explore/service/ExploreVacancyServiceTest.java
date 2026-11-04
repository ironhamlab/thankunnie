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
import com.ucamp.app.domain.treatment.entity.TreatmentType;
import com.ucamp.app.domain.vacancy.policy.PriceTier;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

// 초기 데이터: 매장 1(강남구, 월~토 10~20시)이 2026-11-06(금) 20~22시를 더 열었다. 시술 1 젤네일 60분, 시술 2 손 케어 30분.
// 매장 2(서초구, 화~일 11~21시)는 시술 3 남성 커트 30분, 시술 4 디지털 펌 120분이다. 시작 시각은 30분 단위다.
// 빈자리는 영업시간 + 연 시간 − 닫은 시간 − 예약 기준이다. 지금(11-06 10:00)은 예약 클릭 마감(20분 전) 때문에 10:30 부터 고를 수 있어,
// 11-06 매장 1 은 젤네일 10:30~21:00 22건 + 손 케어 10:30~21:30 23건 = 45건, 매장 2 는 남성 커트 11:00~20:30 20건 + 디지털 펌 11:00~19:00 17건 = 37건이다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ExploreVacancyService.class, AvailabilityService.class,
        ExploreVacancyServiceTest.FixedClock.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class ExploreVacancyServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);
    private static final LocalDate OPEN_DAY = LocalDate.of(2026, 11, 6);
    // 다음 날(토)은 모든 시각이 8시간보다 많이 남아 일반 구간 가격이다. 매장 1 은 젤네일 10:00~19:00 19건 + 손 케어 10:00~19:30 20건
    private static final LocalDate NEXT_DAY = OPEN_DAY.plusDays(1);
    private static final int SHOP_ONE_COUNT = 45;
    private static final int SHOP_TWO_COUNT = 37;

    @Autowired
    private ExploreVacancyService exploreVacancyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("매장이 열지 않은 영업시간도 빈자리이며, 연 시간까지 이어서 클릭 마감 이후 시각을 빠른 시작순으로 낸다")
    void search_businessHoursAndOpen_returnsSlotsInStartOrder() {
        PageResponse<ExploreVacancyResponse> result = exploreVacancyService.search(
                request().shopId(1L).page(0, 50).build());

        assertThat(result.totalElements()).isEqualTo(SHOP_ONE_COUNT);
        assertThat(result.content()).extracting(ExploreVacancyResponse::vacancyId)
                .startsWith("1-202611061030", "2-202611061030", "1-202611061100", "2-202611061100")
                .endsWith("1-202611062100", "2-202611062100", "2-202611062130")
                .doesNotContain("1-202611061000", "2-202611061000");
    }

    @Test
    @DisplayName("응답에 매장 좌표·시각(+09:00)·조회 시점 가격이 담긴다")
    void search_firstItem_hasShopLocationTimesAndPrice() {
        ExploreVacancyResponse first = exploreVacancyService.search(nextDay().shopId(1L).build()).content().get(0);

        assertThat(first.shopName()).isEqualTo("언니네 네일");
        assertThat(first.regionCode()).isEqualTo("11680");
        assertThat(first.latitude()).isEqualByComparingTo("37.4979");
        assertThat(first.longitude()).isEqualByComparingTo("127.0276");
        assertThat(first.categoryName()).isEqualTo("네일");
        assertThat(first.startsAt()).isEqualTo(kst(2026, 11, 7, 10, 0));
        assertThat(first.endsAt()).isEqualTo(kst(2026, 11, 7, 11, 0));
        assertThat(first.bookingClosesAt()).isEqualTo(kst(2026, 11, 7, 9, 40));
        // 24시간 남아 일반 구간: 정가 35,000 → 판매가 30,000, 할인율 14%(버림), 예약금 20,000
        assertThat(first.priceTier()).isEqualTo(PriceTier.NORMAL);
        assertThat(first.regularPrice()).isEqualTo(35000);
        assertThat(first.salePrice()).isEqualTo(30000);
        assertThat(first.discountRate()).isEqualTo(14);
        assertThat(first.depositAmount()).isEqualTo(20000);
    }

    @Test
    @DisplayName("날짜를 생략하면 오늘부터 7일을 조회한다")
    void search_noDates_searchesSevenDaysFromToday() {
        LocalDate today = NOW.toLocalDate();
        long sevenDays = exploreVacancyService.search(
                new RequestBuilder().dates(today, today.plusDays(6)).build()).totalElements();
        long eightDays = exploreVacancyService.search(
                new RequestBuilder().dates(today, today.plusDays(7)).build()).totalElements();

        assertThat(exploreVacancyService.search(new RequestBuilder().build()).totalElements())
                .isEqualTo(sevenDays)
                .isLessThan(eightDays);
    }

    @Test
    @DisplayName("매장을 지정하면 그 매장 빈자리만 나온다")
    void search_shopId_returnsOnlyThatShop() {
        PageResponse<ExploreVacancyResponse> shopTwo = exploreVacancyService.search(
                request().shopId(2L).page(0, 50).build());

        assertThat(shopTwo.totalElements()).isEqualTo(SHOP_TWO_COUNT);
        assertThat(shopTwo.content()).extracting(ExploreVacancyResponse::shopId).containsOnly(2L);
        assertThat(exploreVacancyService.search(request().shopId(1L).build()).totalElements())
                .isEqualTo(SHOP_ONE_COUNT);
    }

    @Test
    @DisplayName("지도 매장 요약은 매장마다 한 건이며 최저가·최대 할인율·빈자리 수·가장 빠른 시작을 담는다")
    void searchShops_groupsVacanciesByShop() {
        ExploreShopMapResponse result = exploreVacancyService.searchShops(request().build().filter());

        assertThat(result.hasMore()).isFalse();
        assertThat(result.shops()).extracting(ExploreShopResponse::shopId).containsExactly(1L, 2L);
        // 10:30 시작은 3시간 안이라 초임박가: 손 케어 20,000 → 15,000(25%)
        ExploreShopResponse shopOne = result.shops().get(0);
        assertThat(shopOne.vacancyCount()).isEqualTo(SHOP_ONE_COUNT);
        assertThat(shopOne.minSalePrice()).isEqualTo(15000);
        assertThat(shopOne.maxDiscountRate()).isEqualTo(25);
        assertThat(shopOne.earliestStartsAt()).isEqualTo(kst(2026, 11, 6, 10, 30));
        assertThat(shopOne.earliestPriceTier()).isEqualTo(PriceTier.LAST_MINUTE);
        assertThat(shopOne.latitude()).isEqualByComparingTo("37.4979");
        // 남성 커트 25,000(할인 없음), 디지털 펌 초임박가 90,000 → 70,000(22%). 펌 120분은 21시 전에 끝나야 해 19:00 이 마지막이다
        ExploreShopResponse shopTwo = result.shops().get(1);
        assertThat(shopTwo.vacancyCount()).isEqualTo(SHOP_TWO_COUNT);
        assertThat(shopTwo.minSalePrice()).isEqualTo(25000);
        assertThat(shopTwo.maxDiscountRate()).isEqualTo(22);
        assertThat(shopTwo.earliestStartsAt()).isEqualTo(kst(2026, 11, 6, 11, 0));
    }

    @Test
    @DisplayName("매장이 연 시간은 영업시간 뒤로 빈자리를 늘린다")
    void searchShops_openAfterBusinessHours_addsSlots() {
        openShopTwo();

        ExploreShopResponse shopTwo = exploreVacancyService.searchShops(request().shopId(2L).build().filter())
                .shops().get(0);

        // 21~22시를 열면 남성 커트 21:00·21:30, 디지털 펌 19:30·20:00 이 늘어난다
        assertThat(shopTwo.vacancyCount()).isEqualTo(SHOP_TWO_COUNT + 4);
    }

    @Test
    @DisplayName("지도 매장 요약도 지도 범위·필터를 적용한다")
    void searchShops_appliesFilters() {
        assertThat(exploreVacancyService.searchShops(request()
                .bounds("37.49", "37.50", "127.02", "127.03").build().filter()).shops())
                .extracting(ExploreShopResponse::shopId).containsExactly(1L);
        assertThat(exploreVacancyService.searchShops(request().categoryId(2L).build().filter()).shops())
                .extracting(ExploreShopResponse::shopId).containsExactly(2L);
    }

    @Test
    @DisplayName("쉬는 날이라 빈자리가 없는 매장은 지도 매장 요약에서 빠진다")
    void searchShops_closedDay_excludesShop() {
        jdbcTemplate.update("UPDATE SHOP_BUSINESS_HOUR SET IS_CLOSED = 'Y', OPEN_TIME = NULL, CLOSE_TIME = NULL"
                + " WHERE SHOP_ID = 2 AND DAY_OF_WEEK = 'FRI'");

        assertThat(exploreVacancyService.searchShops(request().build().filter()).shops())
                .extracting(ExploreShopResponse::shopId).containsExactly(1L);
    }

    @Test
    @DisplayName("시술 유형을 지정하면 그 유형의 빈자리만 나오고, 매장 요약은 초보 시술만 있는지 알려준다")
    void search_treatmentType_returnsOnlyThatType() {
        jdbcTemplate.update("UPDATE TREATMENT SET TREATMENT_TYPE = 'MODEL', MODEL_CONDITION = '조건',"
                + " PHOTO_NOTICE = '안내' WHERE TREATMENT_ID = 2");

        assertThat(exploreVacancyService.search(request().treatmentType(TreatmentType.MODEL).build()).content())
                .extracting(ExploreVacancyResponse::treatmentId).containsOnly(2L);
        assertThat(exploreVacancyService.search(request().treatmentType(TreatmentType.NORMAL).build()).content())
                .extracting(ExploreVacancyResponse::treatmentId).doesNotContain(2L);
        // 지정하지 않으면 기존처럼 두 유형이 모두 나온다
        assertThat(exploreVacancyService.search(request().shopId(1L).build()).totalElements())
                .isEqualTo(SHOP_ONE_COUNT);

        List<ExploreShopResponse> modelShops = exploreVacancyService.searchShops(
                request().treatmentType(TreatmentType.MODEL).build().filter()).shops();
        assertThat(modelShops).extracting(ExploreShopResponse::shopId).containsExactly(1L);
        assertThat(modelShops.get(0).modelOnly()).isTrue();
        assertThat(modelShops.get(0).modelVacancyCount()).isEqualTo(23);
        // 매장 1 은 일반·초보 시술이 섞여 있고, 매장 2 는 일반 시술만 있다
        assertThat(exploreVacancyService.searchShops(request().build().filter()).shops())
                .extracting(ExploreShopResponse::modelOnly).containsExactly(false, false);
    }

    @Test
    @DisplayName("일반·초보 시술이 섞인 매장 요약은 매장 상세에 보이는 일반 빈자리로 계산하고 초보 시술 빈자리는 따로 담는다")
    void searchShops_mixedShop_summarizesNormalAndModelSeparately() {
        // 다음 날 매장 1: 젤네일(일반) 30,000원·14% 19건, 손 케어(초보 시술) 18,000원·10% 20건
        jdbcTemplate.update("UPDATE TREATMENT SET TREATMENT_TYPE = 'MODEL', MODEL_CONDITION = '조건',"
                + " PHOTO_NOTICE = '안내' WHERE TREATMENT_ID = 2");

        ExploreShopResponse shop = exploreVacancyService.searchShops(nextDay().shopId(1L).build().filter())
                .shops().get(0);

        assertThat(shop.modelOnly()).isFalse();
        assertThat(shop.vacancyCount()).isEqualTo(19);
        assertThat(shop.minSalePrice()).isEqualTo(30000);
        assertThat(shop.maxDiscountRate()).isEqualTo(14);
        assertThat(shop.modelVacancyCount()).isEqualTo(20);
        assertThat(shop.modelMinSalePrice()).isEqualTo(18000);
    }

    @Test
    @DisplayName("다른 예약이 잡은 시간과 겹치는 시각은 빠진다")
    void search_reservedTime_excludesOverlappingStarts() {
        jdbcTemplate.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                                         APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (900, 1, 1, 'CONFIRMED', ?, ?, 30000, 20000, ?, ?, ?)
                """, NOW.withHour(20), NOW.withHour(21), NOW, NOW, NOW);

        List<String> ids = exploreVacancyService.search(request().shopId(1L).page(0, 50).build()).content()
                .stream().map(ExploreVacancyResponse::vacancyId).toList();

        // 20~21시와 겹치는 젤네일 19:30·20:00·20:30, 손 케어 20:00·20:30 이 빠진다
        assertThat(ids).hasSize(SHOP_ONE_COUNT - 5)
                .doesNotContain("1-202611061930", "1-202611062000", "1-202611062030",
                        "2-202611062000", "2-202611062030")
                .contains("1-202611061900", "2-202611061930", "1-202611062100", "2-202611062100");
    }

    @Test
    @DisplayName("지역·카테고리·검색어·지도 범위 조건이 매장과 시술에 적용된다")
    void search_shopAndTreatmentFilters_applied() {
        assertThat(exploreVacancyService.search(request().regionCodes(List.of("11680")).build())
                .totalElements()).isEqualTo(SHOP_ONE_COUNT);
        assertThat(exploreVacancyService.search(request().regionCodes(List.of("11650")).build())
                .totalElements()).isEqualTo(SHOP_TWO_COUNT);
        assertThat(exploreVacancyService.search(request().regionCodes(List.of("11110")).build())
                .totalElements()).isZero();
        assertThat(exploreVacancyService.search(request().categoryId(2L).build()).totalElements())
                .isEqualTo(SHOP_TWO_COUNT);
        assertThat(exploreVacancyService.search(request().keyword("케어").page(0, 50).build()).content())
                .allMatch(item -> item.treatmentId() == 2L).hasSize(23);
        assertThat(exploreVacancyService.search(request()
                .bounds("37.49", "37.50", "127.02", "127.03").build()).totalElements()).isEqualTo(SHOP_ONE_COUNT);
        assertThat(exploreVacancyService.search(request()
                .bounds("37.50", "37.60", "127.02", "127.03").build()).totalElements()).isZero();
    }

    @Test
    @DisplayName("검색어로 매장 주소(지역)도 찾는다")
    void search_keyword_matchesShopAddress() {
        assertThat(exploreVacancyService.search(request().keyword("테헤란로").build()).totalElements())
                .isEqualTo(SHOP_ONE_COUNT);
        assertThat(exploreVacancyService.search(request().keyword("서초구").page(0, 50).build()).content())
                .extracting(ExploreVacancyResponse::shopId).containsOnly(2L);
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
        assertThat(exploreVacancyService.search(request().shopId(1L).page(0, 50).build()).content())
                .allMatch(item -> item.treatmentId() == 1L).hasSize(22);

        jdbcTemplate.update("UPDATE SHOP SET STATUS = 'INACTIVE' WHERE SHOP_ID = 1");
        assertThat(exploreVacancyService.search(request().shopId(1L).build()).totalElements()).isZero();
    }

    @Test
    @DisplayName("시간대·마감 임박·가격 구간 조건은 시작 시각 기준으로 거른다")
    void search_timeFilters_applied() {
        // 18시 이후: 젤네일 18:00~21:00 7건 + 손 케어 18:00~21:30 8건
        assertThat(exploreVacancyService.search(request().shopId(1L).timeOfDay(ExploreTimeOfDay.EVENING).build())
                .totalElements()).isEqualTo(15);
        // 12시 전: 10:30·11:00·11:30 시술 둘씩
        assertThat(exploreVacancyService.search(request().shopId(1L).timeOfDay(ExploreTimeOfDay.MORNING).build())
                .totalElements()).isEqualTo(6);
        // 10시간 안에 마감(20:00 이하)되는 것은 20:00 시작까지다
        assertThat(exploreVacancyService.search(request().shopId(1L).closingWithinHours(10).page(0, 50).build())
                .content()).extracting(ExploreVacancyResponse::vacancyId)
                .hasSize(40)
                .contains("1-202611062000", "2-202611062000")
                .doesNotContain("2-202611062030");
        // 임박 이상(8시간 이내, 정확히 8시간 포함)은 18:00 시작까지다
        assertThat(exploreVacancyService.search(request().shopId(1L).priceTier(PriceTier.IMMINENT).page(0, 50)
                .build()).content())
                .hasSize(32)
                .allSatisfy(item -> assertThat(item.priceTier()).isNotEqualTo(PriceTier.NORMAL))
                .extracting(ExploreVacancyResponse::vacancyId)
                .contains("1-202611061800")
                .doesNotContain("1-202611061830");
    }

    // 타임세일(초임박): 지금(10:00)부터 13:00 까지 영업시간 안 빈 시각. 매장 1(금 10~20시)은 클릭 마감 20분 뒤 10:30,
    // 매장 2(금 11~21시)는 11:00 부터. 남성 커트(시술 3)는 초임박가가 판매가와 같아 빠진다
    @Test
    @DisplayName("타임세일은 매장이 열지 않아도 영업시간 안 빈 시각을 시작 3시간 이내만 낸다")
    void search_lastMinuteTier_usesBusinessHoursWithinThreeHours() {
        List<ExploreVacancyResponse> content = saleSearch(request()).content();

        assertThat(content).extracting(ExploreVacancyResponse::vacancyId)
                .startsWith("1-202611061030", "2-202611061030")
                .contains("4-202611061100", "1-202611061300")
                .doesNotContain("1-202611061330");
        assertThat(content).allSatisfy(item -> {
            assertThat(item.startsAt()).isBeforeOrEqualTo(kst(2026, 11, 6, 13, 0));
            assertThat(item.priceTier()).isEqualTo(PriceTier.LAST_MINUTE);
        });
    }

    @Test
    @DisplayName("타임세일은 예약 클릭 마감(시작 20분 전)이 지난 시각을 내지 않는다")
    void search_lastMinuteTier_afterBookingDeadline_excluded() {
        assertThat(saleSearch(request()).content()).extracting(ExploreVacancyResponse::vacancyId)
                .doesNotContain("1-202611061000", "2-202611061000");
    }

    @Test
    @DisplayName("타임세일은 초임박가가 일반 판매가보다 싼 시술만 낸다")
    void search_lastMinuteTier_withoutDiscount_excluded() {
        jdbcTemplate.update("UPDATE TREATMENT SET LAST_MINUTE_PRICE = PRICE WHERE TREATMENT_ID = 2");

        assertThat(saleSearch(request()).content()).extracting(ExploreVacancyResponse::vacancyId)
                .isNotEmpty()
                .noneMatch(id -> id.startsWith("2-") || id.startsWith("3-"));
    }

    @Test
    @DisplayName("타임세일 매장 요약은 열지 않은 매장도 담고, 초보 시술·매장 조건은 그대로 적용한다")
    void searchShops_lastMinuteTier_includesShopsWithoutOpenAndAppliesFilters() {
        jdbcTemplate.update("UPDATE TREATMENT SET TREATMENT_TYPE = 'MODEL', MODEL_CONDITION = '조건'"
                + " WHERE TREATMENT_ID = 2");

        ExploreShopMapResponse result = exploreVacancyService.searchShops(
                request().priceTier(PriceTier.LAST_MINUTE).build().filter());

        assertThat(result.shops()).extracting(ExploreShopResponse::shopId).containsExactly(1L, 2L);
        assertThat(result.shops()).extracting(ExploreShopResponse::earliestPriceTier)
                .containsOnly(PriceTier.LAST_MINUTE);
        assertThat(result.shops().get(0).modelVacancyCount()).isPositive();
        assertThat(saleSearch(request().treatmentType(TreatmentType.MODEL)).content())
                .isNotEmpty()
                .allSatisfy(item -> assertThat(item.vacancyId()).startsWith("2-"));
        assertThat(saleSearch(request().shopId(2L)).content())
                .extracting(ExploreVacancyResponse::shopId).containsOnly(2L);
    }

    @Test
    @DisplayName("판매가 낮은 순으로 정렬하면 같은 가격은 시작 시각 순이다")
    void search_priceAsc_sortsByPriceThenStart() {
        // 다음 날 매장 1: 손 케어 18,000 20건 다음 젤네일 30,000 19건
        List<String> ids = exploreVacancyService.search(nextDay().shopId(1L).sort(ExploreVacancySort.PRICE_ASC)
                .page(0, 50).build()).content().stream().map(ExploreVacancyResponse::vacancyId).toList();

        assertThat(ids).hasSize(39)
                .startsWith("2-202611071000", "2-202611071030")
                .endsWith("1-202611071830", "1-202611071900");
        assertThat(ids.get(20)).isEqualTo("1-202611071000");
    }

    @Test
    @DisplayName("페이지를 나누면 전체 개수는 유지하고 해당 페이지만 돌려준다")
    void search_paging_returnsRequestedPage() {
        PageResponse<ExploreVacancyResponse> page = exploreVacancyService.search(
                request().shopId(1L).page(1, 3).build());

        assertThat(page.content()).extracting(ExploreVacancyResponse::vacancyId)
                .containsExactly("2-202611061100", "1-202611061130", "2-202611061130");
        assertThat(page.totalElements()).isEqualTo(SHOP_ONE_COUNT);
        assertThat(page.totalPages()).isEqualTo(15);
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

    // 임박 빈자리: 지금(10:00)부터 18:00 전까지 시작. 매장 1(금 10~20시)은 클릭 마감 20분 뒤 첫 시각 10:30,
    // 매장 2(금 11~21시)는 11:00 이 가장 빠르다. 같은 시각이면 할인율이 높은 시술을 고른다
    @Test
    @DisplayName("임박 빈자리는 매장이 열지 않아도 영업시간 안 빈 시각에서 매장마다 가장 빠른 1건을 빠른 시작순으로 낸다")
    void searchImminent_withoutOpen_returnsEarliestPerShop() {
        List<ExploreVacancyResponse> result = exploreVacancyService.searchImminent();

        // 매장 1 10:30: 손 케어 25%(20,000→15,000) > 젤네일 22%. 매장 2 11:00: 디지털 펌 22% > 남성 커트 0%
        assertThat(result).extracting(ExploreVacancyResponse::vacancyId)
                .containsExactly("2-202611061030", "4-202611061100");
        assertThat(result.get(0).priceTier()).isEqualTo(PriceTier.LAST_MINUTE);
    }

    @Test
    @DisplayName("임박 빈자리에서 초보 시술은 빠진다")
    void searchImminent_modelTreatment_excluded() {
        jdbcTemplate.update("UPDATE TREATMENT SET TREATMENT_TYPE = 'MODEL', MODEL_CONDITION = '조건'"
                + " WHERE TREATMENT_ID = 2");

        assertThat(exploreVacancyService.searchImminent()).extracting(ExploreVacancyResponse::vacancyId)
                .containsExactly("1-202611061030", "4-202611061100");
    }

    @Test
    @DisplayName("임박 빈자리는 지금부터 8시간 안에 시작하는 시각만 낸다")
    void searchImminent_afterEightHours_excluded() {
        jdbcTemplate.update("UPDATE SHOP_BUSINESS_HOUR SET OPEN_TIME = '18:00' WHERE SHOP_ID = 2 AND DAY_OF_WEEK = 'FRI'");

        assertThat(exploreVacancyService.searchImminent()).extracting(ExploreVacancyResponse::shopId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("임박 빈자리는 8시간이 되기 직전 시각까지 낸다")
    void searchImminent_justBeforeEightHours_included() {
        jdbcTemplate.update("UPDATE SHOP_BUSINESS_HOUR SET OPEN_TIME = '17:30' WHERE SHOP_ID = 2 AND DAY_OF_WEEK = 'FRI'");

        assertThat(exploreVacancyService.searchImminent()).extracting(ExploreVacancyResponse::vacancyId)
                .containsExactly("2-202611061030", "4-202611061730");
    }

    @Test
    @DisplayName("임박 빈자리는 예약과 겹치는 시각을 빼고 그다음 빠른 시각을 낸다")
    void searchImminent_reservedTime_picksNextEarliest() {
        jdbcTemplate.update("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                                         APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CREATED_AT, UPDATED_AT)
                VALUES (900, 1, 1, 'CONFIRMED', ?, ?, 30000, 20000, ?, ?, ?)
                """, NOW.withMinute(30), NOW.withHour(12), NOW, NOW, NOW);

        assertThat(exploreVacancyService.searchImminent()).extracting(ExploreVacancyResponse::vacancyId)
                .containsExactly("4-202611061100", "2-202611061200");
    }

    @Test
    @DisplayName("임박 빈자리에서 노출하지 않는 매장은 빠진다")
    void searchImminent_inactiveShop_excluded() {
        jdbcTemplate.update("UPDATE SHOP SET STATUS = 'INACTIVE' WHERE SHOP_ID = 2");

        assertThat(exploreVacancyService.searchImminent()).extracting(ExploreVacancyResponse::shopId)
                .containsExactly(1L);
    }

    // 매장 2(서초구, 금 11~21시)가 2026-11-06 21~22시를 연다
    private void openShopTwo() {
        jdbcTemplate.update("""
                INSERT INTO SHOP_SCHEDULE_EXCEPTION (EXCEPTION_ID, SHOP_ID, RESERVATION_ID, EXCEPTION_TYPE,
                                                     STARTS_AT, ENDS_AT, REASON, CREATED_AT, UPDATED_AT)
                VALUES (900, 2, NULL, 'OPEN', ?, ?, '연장', ?, ?)
                """, NOW.withHour(21), NOW.withHour(22), NOW, NOW);
    }

    private PageResponse<ExploreVacancyResponse> saleSearch(RequestBuilder builder) {
        return exploreVacancyService.search(builder.priceTier(PriceTier.LAST_MINUTE).page(0, 50).build());
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

    private static RequestBuilder nextDay() {
        return new RequestBuilder().dates(NEXT_DAY, NEXT_DAY);
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
        private TreatmentType treatmentType;
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

        RequestBuilder treatmentType(TreatmentType value) {
            this.treatmentType = value;
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
                    shopId, treatmentType, timeOfDay, closingWithinHours, priceTier, keyword, minLat, maxLat, minLng,
                    maxLng);
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
