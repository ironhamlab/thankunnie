package com.ucamp.app.domain.shop.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.StubAuthFilter;
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.config.SecurityConfig;
import com.ucamp.app.domain.shop.dto.request.ShopCustomerSegmentFilter;
import com.ucamp.app.domain.shop.dto.request.ShopCustomerSort;
import com.ucamp.app.domain.shop.dto.response.ShopCustomerMemoResponse;
import com.ucamp.app.domain.shop.dto.response.ShopCustomerResponse;
import com.ucamp.app.domain.shop.policy.CustomerSegment;
import com.ucamp.app.domain.shop.service.ShopCustomerService;

@WebMvcTest(ShopCustomerController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ShopCustomerControllerTest {

    private static final String URL = "/api/v1/owner/customers";
    private static final OffsetDateTime AT = OffsetDateTime.of(2026, 11, 6, 10, 0, 0, 0, ZoneOffset.ofHours(9));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShopCustomerService shopCustomerService;

    @Test
    @DisplayName("고객 목록은 분류·정렬·검색어를 넘기고 페이지로 반환한다")
    void getCustomers_owner_returnsPage() throws Exception {
        ShopCustomerResponse customer = new ShopCustomerResponse(106L, "김하은", "010-6666-0106", 7, 238000,
                AT.minusDays(30), AT, null, CustomerSegment.RETURNING, true);
        given(shopCustomerService.getCustomers(any(AuthUser.class), eq(ShopCustomerSegmentFilter.RETURNING),
                eq(ShopCustomerSort.VISITS), eq("하은"), eq(0), eq(20)))
                .willReturn(PageResponse.of(List.of(customer), 0, 20, 1));

        mockMvc.perform(get(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .param("segment", "RETURNING").param("sort", "VISITS").param("keyword", "하은"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].visitCount").value(7))
                .andExpect(jsonPath("$.data.content[0].segment").value("RETURNING"))
                .andExpect(jsonPath("$.data.content[0].lastVisitAt").value("2026-11-06T10:00:00+09:00"));
    }

    @Test
    @DisplayName("없는 분류 값은 400 이다")
    void getCustomers_invalidSegment_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL).header(StubAuthFilter.HEADER_NAME, "OWNER_1").param("segment", "VIP"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(shopCustomerService);
    }

    @Test
    @DisplayName("메모를 저장하고, 1,000자를 넘으면 400 이다")
    void saveMemo_savesAndValidatesLength() throws Exception {
        given(shopCustomerService.saveMemo(any(AuthUser.class), eq(106L), eq("누드톤 선호")))
                .willReturn(new ShopCustomerMemoResponse("누드톤 선호", AT));

        mockMvc.perform(put(URL + "/106/memo").header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memo\":\"누드톤 선호\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value("누드톤 선호"));
        mockMvc.perform(put(URL + "/106/memo").header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"" + "가".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비우면 data 는 null 이다")
    void saveMemo_blank_returnsNullData() throws Exception {
        given(shopCustomerService.saveMemo(any(AuthUser.class), eq(106L), isNull())).willReturn(null);

        mockMvc.perform(put(URL + "/106/memo").header(StubAuthFilter.HEADER_NAME, "OWNER_1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("비로그인은 401 이다")
    void anonymous_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/106")).andExpect(status().isUnauthorized());
        verifyNoInteractions(shopCustomerService);
    }
}
