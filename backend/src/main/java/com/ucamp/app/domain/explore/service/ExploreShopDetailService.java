package com.ucamp.app.domain.explore.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.explore.dto.response.ExploreShopDetailResponse;
import com.ucamp.app.domain.explore.repository.ExploreShopCandidate;
import com.ucamp.app.domain.explore.repository.ExploreShopDetailRepository;
import com.ucamp.app.domain.schedule.dto.response.BusinessHourResponse;
import com.ucamp.app.domain.schedule.entity.BusinessDay;
import com.ucamp.app.domain.schedule.entity.ShopBusinessHour;
import com.ucamp.app.domain.schedule.repository.ShopBusinessHourRepository;

/**
 * 고객 매장 상세. 로그인 없이 노출 중인 매장의 안내·영업시간·판매 중인 시술을 조회한다.
 */
@Service
@Transactional(readOnly = true)
public class ExploreShopDetailService {

    private final ExploreShopDetailRepository exploreShopDetailRepository;
    private final ShopBusinessHourRepository shopBusinessHourRepository;

    public ExploreShopDetailService(ExploreShopDetailRepository exploreShopDetailRepository,
            ShopBusinessHourRepository shopBusinessHourRepository) {
        this.exploreShopDetailRepository = exploreShopDetailRepository;
        this.shopBusinessHourRepository = shopBusinessHourRepository;
    }

    /**
     * 매장 상세를 조회한다.
     *
     * @param shopId 매장 ID
     * @return 매장 상세
     */
    public ExploreShopDetailResponse getShop(Long shopId) {
        ExploreShopCandidate shop = exploreShopDetailRepository.findShop(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "매장을 찾을 수 없습니다."));
        return ExploreShopDetailResponse.of(shop, businessHours(shopId),
                exploreShopDetailRepository.findTreatments(shopId));
    }

    // 사장님 영업시간 조회와 같이 월~일 7개를 돌려주고, 저장하지 않은 요일은 휴무로 본다
    private List<BusinessHourResponse> businessHours(Long shopId) {
        Map<BusinessDay, ShopBusinessHour> hours = shopBusinessHourRepository.findByShopId(shopId).stream()
                .collect(Collectors.toMap(ShopBusinessHour::getDayOfWeek, Function.identity()));
        return Arrays.stream(BusinessDay.values())
                .map(day -> hours.containsKey(day)
                        ? BusinessHourResponse.from(hours.get(day))
                        : BusinessHourResponse.closedDay(day))
                .toList();
    }
}
