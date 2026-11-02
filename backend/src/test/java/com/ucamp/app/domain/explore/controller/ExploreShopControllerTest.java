package com.ucamp.app.domain.explore.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.explore.dto.response.ExploreShopDetailResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreShopListResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreShopDetailResponse.ShopTreatment;
import com.ucamp.app.domain.explore.service.ExploreShopDetailService;
import com.ucamp.app.domain.schedule.dto.response.BusinessHourResponse;
import com.ucamp.app.domain.schedule.entity.BusinessDay;
import com.ucamp.app.domain.treatment.entity.TreatmentType;

@WebMvcTest(controllers = ExploreShopController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ExploreShopControllerTest {

    private static final String SHOP_URL = "/api/v1/explore/shops/{shopId}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExploreShopDetailService exploreShopDetailService;

    @Test
    @DisplayName("로그인 없이 매장 상세를 조회하고 영업시간은 HH:mm 으로 응답한다")
    void getShop_anonymous_ok() throws Exception {
        ExploreShopDetailResponse response = new ExploreShopDetailResponse(1L, "언니네 네일", 1L, "네일", "11680",
                "서울특별시 강남구 테헤란로 1", "2층", new BigDecimal("37.4979000"), new BigDecimal("127.0276000"),
                "02-111-1111", "젤네일·케어 전문", null, null,
                List.of(new BusinessHourResponse(BusinessDay.MON, false, LocalTime.of(10, 0), LocalTime.of(20, 0))),
                List.of(new ShopTreatment(1L, "젤네일 원컬러", "기본 원컬러 젤네일", TreatmentType.NORMAL, 60, 35000,
                        30000, null, null, null)));
        given(exploreShopDetailService.getShop(1L)).willReturn(response);

        mockMvc.perform(get(SHOP_URL, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shopName").value("언니네 네일"))
                .andExpect(jsonPath("$.data.businessHours[0].dayOfWeek").value("MON"))
                .andExpect(jsonPath("$.data.businessHours[0].openTime").value("10:00"))
                .andExpect(jsonPath("$.data.treatments[0].treatmentId").value(1))
                .andExpect(jsonPath("$.data.treatments[0].price").value(30000));
    }

    @Test
    @DisplayName("없는 매장은 404 공통 오류로 응답한다")
    void getShop_missing_notFound() throws Exception {
        given(exploreShopDetailService.getShop(9999L))
                .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "매장을 찾을 수 없습니다."));

        mockMvc.perform(get(SHOP_URL, 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON_404"));
    }

    @Test
    @DisplayName("로그인 없이 전체 매장 목록을 조회하고, 매장 상세 주소와 겹치지 않는다")
    void getAllShops_anonymous_ok() throws Exception {
        given(exploreShopDetailService.getShops(1L, "언니")).willReturn(List.of(
                new ExploreShopListResponse(1L, "언니네 네일", 1L, "네일", "11680", "서울특별시 강남구 테헤란로 1",
                        new BigDecimal("37.4979000"), new BigDecimal("127.0276000"), null)));

        mockMvc.perform(get("/api/v1/explore/shops/all").param("categoryId", "1").param("keyword", "언니"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].shopId").value(1))
                .andExpect(jsonPath("$.data[0].categoryName").value("네일"));
    }
}
