package com.ucamp.app.domain.explore.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.explore.dto.response.ExploreShopDetailResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreShopDetailResponse.ShopTreatment;
import com.ucamp.app.domain.schedule.dto.response.BusinessHourResponse;
import com.ucamp.app.domain.schedule.entity.BusinessDay;

// 초기 데이터: 매장 1(언니네 네일) 월~토 10~20시·일 휴무, 시술 1 젤네일 60분·시술 2 손 케어 30분.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ExploreShopDetailService.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class ExploreShopDetailServiceTest {

    @Autowired
    private ExploreShopDetailService exploreShopDetailService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("매장 안내, 월~일 영업시간, 판매 중인 시술을 시술 ID 순으로 돌려준다")
    void getShop_activeShop_returnsDetail() {
        ExploreShopDetailResponse shop = exploreShopDetailService.getShop(1L);

        assertThat(shop.shopName()).isEqualTo("언니네 네일");
        assertThat(shop.categoryName()).isEqualTo("네일");
        assertThat(shop.addressDetail()).isEqualTo("2층");
        assertThat(shop.phone()).isEqualTo("02-111-1111");
        assertThat(shop.description()).isEqualTo("젤네일·케어 전문");
        assertThat(shop.notice()).isEqualTo("예약 시간 10분 전 도착해 주세요");
        assertThat(shop.businessHours()).extracting(BusinessHourResponse::dayOfWeek)
                .containsExactly(BusinessDay.values());
        assertThat(shop.businessHours().get(0))
                .isEqualTo(new BusinessHourResponse(BusinessDay.MON, false, LocalTime.of(10, 0), LocalTime.of(20, 0)));
        assertThat(shop.businessHours().get(6)).isEqualTo(BusinessHourResponse.closedDay(BusinessDay.SUN));
        assertThat(shop.treatments()).extracting(ShopTreatment::treatmentId).containsExactly(1L, 2L);
        assertThat(shop.treatments().get(0).durationMinutes()).isEqualTo(60);
        assertThat(shop.treatments().get(0).regularPrice()).isEqualTo(35000);
        assertThat(shop.treatments().get(0).price()).isEqualTo(30000);
    }

    @Test
    @DisplayName("판매 중지한 시술은 목록에서 빠진다")
    void getShop_inactiveTreatment_excluded() {
        jdbcTemplate.update("UPDATE TREATMENT SET STATUS = 'INACTIVE' WHERE TREATMENT_ID = 2");

        assertThat(exploreShopDetailService.getShop(1L).treatments())
                .extracting(ShopTreatment::treatmentId).containsExactly(1L);
    }

    @Test
    @DisplayName("모델 모집 시술은 참여 조건과 촬영 안내를 함께 돌려준다")
    void getShop_modelTreatment_returnsModelNotices() {
        jdbcTemplate.update("UPDATE TREATMENT SET TREATMENT_TYPE = 'MODEL', MODEL_CONDITION = '탈색 이력이 없는 분',"
                + " PHOTO_NOTICE = '전후 사진 촬영' WHERE TREATMENT_ID = 2");

        ShopTreatment treatment = exploreShopDetailService.getShop(1L).treatments().get(1);

        assertThat(treatment.modelCondition()).isEqualTo("탈색 이력이 없는 분");
        assertThat(treatment.photoNotice()).isEqualTo("전후 사진 촬영");
    }

    @Test
    @DisplayName("저장하지 않은 요일은 휴무로 채운다")
    void getShop_missingBusinessDay_closed() {
        jdbcTemplate.update("DELETE FROM SHOP_BUSINESS_HOUR WHERE SHOP_ID = 1 AND DAY_OF_WEEK = 'MON'");

        assertThat(exploreShopDetailService.getShop(1L).businessHours().get(0))
                .isEqualTo(BusinessHourResponse.closedDay(BusinessDay.MON));
    }

    @Test
    @DisplayName("비활성 매장과 없는 매장은 404 로 알린다")
    void getShop_inactiveOrMissing_notFound() {
        jdbcTemplate.update("UPDATE SHOP SET STATUS = 'INACTIVE' WHERE SHOP_ID = 1");

        assertThatThrownBy(() -> exploreShopDetailService.getShop(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(() -> exploreShopDetailService.getShop(9999L))
                .isInstanceOf(BusinessException.class);
    }
}
