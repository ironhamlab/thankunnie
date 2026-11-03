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
import com.ucamp.app.domain.explore.dto.response.ExploreShopListResponse;
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

    private static final int MAX_KEYWORD_LENGTH = 50;

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

    /**
     * 노출 중인 매장 전체를 빈자리 유무와 상관없이 조회한다.
     *
     * @param categoryId 해당 분야 매장만. null 이면 전체
     * @param keyword    매장명·주소(지역)·판매 중인 시술명 검색어. 비어 있으면 조건 없음
     * @return 매장 ID 순 매장 목록
     */
    public List<ExploreShopListResponse> getShops(Long categoryId, String keyword) {
        return exploreShopDetailRepository.findShops(categoryId, keywordPattern(keyword)).stream()
                .map(ExploreShopListResponse::from)
                .toList();
    }

    private static String keywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "keyword 는 50자 이하여야 합니다.");
        }
        String escaped = trimmed.toUpperCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    // 매장 영업시간 조회와 같이 월~일 7개를 돌려주고, 저장하지 않은 요일은 휴무로 본다
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
