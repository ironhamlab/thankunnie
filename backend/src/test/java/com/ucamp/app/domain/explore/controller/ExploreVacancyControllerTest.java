package com.ucamp.app.domain.explore.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.explore.dto.request.ExploreTimeOfDay;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancyFilter;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancySearchRequest;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancySort;
import com.ucamp.app.domain.explore.dto.response.ExploreShopMapResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreShopResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreVacancyDetailResponse;
import com.ucamp.app.domain.explore.service.ExploreVacancyDetailService;
import com.ucamp.app.domain.explore.service.ExploreVacancyService;
import com.ucamp.app.domain.treatment.entity.TreatmentType;
import com.ucamp.app.domain.vacancy.policy.PriceTier;

@WebMvcTest(controllers = ExploreVacancyController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ExploreVacancyControllerTest {

    private static final String VACANCIES_URL = "/api/v1/explore/vacancies";
    private static final String SHOPS_URL = "/api/v1/explore/shops";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExploreVacancyService exploreVacancyService;

    @MockitoBean
    private ExploreVacancyDetailService exploreVacancyDetailService;

    @Test
    @DisplayName("비로그인으로 목록을 조회하면 페이지 응답을 반환하고, 생략한 정렬·페이지는 기본값으로 넘긴다")
    void getVacancies_anonymous_usesDefaults() throws Exception {
        given(exploreVacancyService.search(any())).willReturn(PageResponse.of(List.of(), 0, 20, 0));

        mockMvc.perform(get(VACANCIES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));

        ArgumentCaptor<ExploreVacancySearchRequest> captor = ArgumentCaptor.forClass(ExploreVacancySearchRequest.class);
        verify(exploreVacancyService).search(captor.capture());
        ExploreVacancySearchRequest request = captor.getValue();
        assertThat(request.sort()).isEqualTo(ExploreVacancySort.START_ASC);
        assertThat(request.page()).isZero();
        assertThat(request.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("반복된 지역 코드와 매장·필터·지도 범위 값을 그대로 넘긴다")
    void getVacancies_allParams_bound() throws Exception {
        given(exploreVacancyService.search(any())).willReturn(PageResponse.of(List.of(), 1, 50, 0));

        mockMvc.perform(get(VACANCIES_URL)
                        .param("startDate", "2026-11-06").param("endDate", "2026-11-07")
                        .param("regionCode", "11680").param("regionCode", "11650")
                        .param("categoryId", "1").param("shopId", "3").param("treatmentType", "MODEL")
                        .param("timeOfDay", "EVENING")
                        .param("closingWithinHours", "24").param("priceTier", "IMMINENT")
                        .param("keyword", "네일")
                        .param("minLat", "37.48").param("maxLat", "37.52")
                        .param("minLng", "127.00").param("maxLng", "127.06")
                        .param("sort", "DISCOUNT_DESC").param("page", "1").param("size", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<ExploreVacancySearchRequest> captor = ArgumentCaptor.forClass(ExploreVacancySearchRequest.class);
        verify(exploreVacancyService).search(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ExploreVacancySearchRequest(
                new ExploreVacancyFilter(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 7),
                        List.of("11680", "11650"), 1L, 3L, TreatmentType.MODEL, ExploreTimeOfDay.EVENING, 24,
                        PriceTier.IMMINENT, "네일",
                        new BigDecimal("37.48"), new BigDecimal("37.52"),
                        new BigDecimal("127.00"), new BigDecimal("127.06")),
                ExploreVacancySort.DISCOUNT_DESC, 1, 50));
    }

    @Test
    @DisplayName("지원하지 않는 정렬·시간대 값이나 날짜 형식 오류는 400 이다")
    void getVacancies_invalidParams_returnsBadRequest() throws Exception {
        mockMvc.perform(get(VACANCIES_URL).param("sort", "NAME_ASC")).andExpect(status().isBadRequest());
        mockMvc.perform(get(VACANCIES_URL).param("timeOfDay", "NIGHT")).andExpect(status().isBadRequest());
        mockMvc.perform(get(VACANCIES_URL).param("startDate", "11/06")).andExpect(status().isBadRequest());
        verifyNoInteractions(exploreVacancyService);
    }

    @Test
    @DisplayName("빈자리 상세는 비로그인으로 조회하고 시술 ID 와 시작 시각을 넘긴다")
    void getVacancyDetail_anonymous_returnsDetail() throws Exception {
        OffsetDateTime startsAt = OffsetDateTime.of(2026, 11, 6, 20, 0, 0, 0, ZoneOffset.ofHours(9));
        ExploreVacancyDetailResponse detail = new ExploreVacancyDetailResponse("1-202611062000", 1L, "언니네 네일",
                "11680", "서울특별시 강남구 테헤란로 1", new BigDecimal("37.4979"), new BigDecimal("127.0276"),
                1L, "젤네일 원컬러", 1L, "네일", "NORMAL", 60, null, startsAt, startsAt.plusHours(1),
                startsAt.minusMinutes(20), PriceTier.NORMAL, 35000, 30000, 14, 20000, true, null,
                "기본 원컬러 젤네일", null, null, null, "2층", "02-111-1111", "젤네일·케어 전문", null, 10000);
        given(exploreVacancyDetailService.getDetail(eq(1L), any())).willReturn(detail);

        mockMvc.perform(get(VACANCIES_URL + "/1").param("startsAt", "2026-11-06T20:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vacancyId").value("1-202611062000"))
                .andExpect(jsonPath("$.data.bookable").value(true))
                .andExpect(jsonPath("$.data.notBookableReason").doesNotExist())
                .andExpect(jsonPath("$.data.startsAt").value("2026-11-06T20:00:00+09:00"))
                .andExpect(jsonPath("$.data.balanceAmount").value(10000));

        verify(exploreVacancyDetailService).getDetail(1L, LocalDateTime.of(2026, 11, 6, 20, 0));
    }

    @Test
    @DisplayName("빈자리 상세에서 시작 시각이 없거나 형식이 틀리면 400 이다")
    void getVacancyDetail_invalidStartsAt_returnsBadRequest() throws Exception {
        mockMvc.perform(get(VACANCIES_URL + "/1")).andExpect(status().isBadRequest());
        mockMvc.perform(get(VACANCIES_URL + "/1").param("startsAt", "2026-11-06")).andExpect(status().isBadRequest());
        mockMvc.perform(get(VACANCIES_URL + "/abc").param("startsAt", "2026-11-06T20:00:00"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(exploreVacancyDetailService);
    }

    @Test
    @DisplayName("지도 매장 요약은 비로그인으로 조회하고 마커용 값을 +09:00 시각으로 반환한다")
    void getShops_anonymous_returnsShops() throws Exception {
        ExploreShopResponse shop = new ExploreShopResponse(1L, "언니네 네일", "11680", "서울특별시 강남구 테헤란로 1",
                new BigDecimal("37.4979"), new BigDecimal("127.0276"), null, 7, 18000, 14,
                OffsetDateTime.of(2026, 11, 6, 20, 0, 0, 0, ZoneOffset.ofHours(9)), true, 7, 18000);
        given(exploreVacancyService.searchShops(any())).willReturn(new ExploreShopMapResponse(List.of(shop), false));

        mockMvc.perform(get(SHOPS_URL).param("minLat", "37.48").param("maxLat", "37.52")
                        .param("minLng", "127.00").param("maxLng", "127.06").param("treatmentType", "MODEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shops[0].shopId").value(1))
                .andExpect(jsonPath("$.data.shops[0].minSalePrice").value(18000))
                .andExpect(jsonPath("$.data.shops[0].earliestStartsAt").value("2026-11-06T20:00:00+09:00"))
                .andExpect(jsonPath("$.data.shops[0].modelOnly").value(true))
                .andExpect(jsonPath("$.data.hasMore").value(false));

        ArgumentCaptor<ExploreVacancyFilter> captor = ArgumentCaptor.forClass(ExploreVacancyFilter.class);
        verify(exploreVacancyService).searchShops(captor.capture());
        assertThat(captor.getValue().minLat()).isEqualByComparingTo("37.48");
        assertThat(captor.getValue().shopId()).isNull();
        assertThat(captor.getValue().treatmentType()).isEqualTo(TreatmentType.MODEL);
    }

    @Test
    @DisplayName("지도 매장 요약도 잘못된 값이면 400 이다")
    void getShops_invalidParams_returnsBadRequest() throws Exception {
        mockMvc.perform(get(SHOPS_URL).param("priceTier", "CHEAP")).andExpect(status().isBadRequest());
        mockMvc.perform(get(SHOPS_URL).param("minLat", "north")).andExpect(status().isBadRequest());
        verifyNoInteractions(exploreVacancyService);
    }
}
