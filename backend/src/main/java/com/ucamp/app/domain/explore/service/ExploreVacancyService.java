package com.ucamp.app.domain.explore.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancyFilter;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancySearchRequest;
import com.ucamp.app.domain.explore.dto.request.ExploreVacancySort;
import com.ucamp.app.domain.explore.dto.response.ExploreShopMapResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreShopResponse;
import com.ucamp.app.domain.explore.dto.response.ExploreVacancyResponse;
import com.ucamp.app.domain.explore.repository.ExploreVacancyCandidate;
import com.ucamp.app.domain.explore.repository.ExploreVacancyCondition;
import com.ucamp.app.domain.explore.repository.ExploreVacancyCondition.MapBounds;
import com.ucamp.app.domain.explore.repository.ExploreVacancyQueryRepository;
import com.ucamp.app.domain.treatment.entity.TreatmentType;
import com.ucamp.app.domain.vacancy.policy.PriceCalculator;
import com.ucamp.app.domain.vacancy.policy.PriceQuote;
import com.ucamp.app.domain.vacancy.policy.PriceTier;
import com.ucamp.app.domain.vacancy.policy.SalePrices;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

/**
 * 고객 빈자리 탐색 (C-01 목록, C-02 지도 매장 요약). 매장이 연 시간대(OPEN) 안에서 지금 예약할 수 있는 시술 × 시작 시각을 구한다.
 * 타임세일(가격 구간 초임박)과 메인 임박 빈자리는 OPEN 없이 영업시간 안 빈 시각에서 구한다.
 * 예약 가능 판단과 가격은 B 의 AvailabilityService·PriceCalculator 를 그대로 쓴다.
 * 시작 시각을 계산한 뒤 거르고 정렬하므로 페이지 처리·매장 묶기는 메모리에서 한다.
 */
@Service
@Transactional(readOnly = true)
public class ExploreVacancyService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_MAP_SHOPS = 200;
    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_REGION_CODES = 10;
    private static final int MAX_KEYWORD_LENGTH = 50;
    private static final int MAX_CLOSING_WITHIN_HOURS = 72;
    private static final int IMMINENT_LIMIT = 10;

    private static final Comparator<Item> TIEBREAK = Comparator
            .comparing((Item item) -> item.startsAt())
            .thenComparing(item -> item.candidate().treatmentId());

    // 임박 빈자리: 빠른 시작 → 높은 할인율 → 시술 ID. 매장 대표 1건을 고를 때와 매장끼리 정렬할 때 같이 쓴다
    private static final Comparator<Item> IMMINENT_ORDER = Comparator
            .comparing((Item item) -> item.startsAt())
            .thenComparing(Comparator.comparingInt((Item item) -> item.quote().discountRate()).reversed())
            .thenComparing(item -> item.candidate().treatmentId());

    private final ExploreVacancyQueryRepository exploreVacancyQueryRepository;
    private final AvailabilityService availabilityService;
    private final Clock clock;

    public ExploreVacancyService(ExploreVacancyQueryRepository exploreVacancyQueryRepository,
            AvailabilityService availabilityService, Clock clock) {
        this.exploreVacancyQueryRepository = exploreVacancyQueryRepository;
        this.availabilityService = availabilityService;
        this.clock = clock;
    }

    /**
     * 빈자리 목록 (C-01).
     */
    public PageResponse<ExploreVacancyResponse> search(ExploreVacancySearchRequest request) {
        validatePage(request.page(), request.size());
        List<Item> items = filteredItems(request.filter(), LocalDateTime.now(clock)).stream()
                .sorted(comparator(request.sort()))
                .toList();

        long offset = (long) request.page() * request.size();
        List<ExploreVacancyResponse> content = items.stream()
                .skip(offset)
                .limit(request.size())
                .map(item -> ExploreVacancyResponse.of(item.candidate(), item.startsAt(), item.quote()))
                .toList();
        return PageResponse.of(content, request.page(), request.size(), items.size());
    }

    /**
     * 지도 마커용 매장 요약 (C-02). 빈자리가 있는 매장만 가장 빠른 시작 시각, 매장 ID 순으로 최대 200개 돌려준다.
     */
    public ExploreShopMapResponse searchShops(ExploreVacancyFilter filter) {
        Map<Long, List<Item>> itemsByShop = filteredItems(filter, LocalDateTime.now(clock)).stream()
                .collect(Collectors.groupingBy(item -> item.candidate().shopId(), LinkedHashMap::new,
                        Collectors.toList()));
        List<ExploreShopResponse> shops = itemsByShop.values().stream()
                .map(ExploreVacancyService::toShop)
                .sorted(Comparator.comparing(ExploreShopResponse::earliestStartsAt)
                        .thenComparing(ExploreShopResponse::shopId))
                .toList();
        return new ExploreShopMapResponse(
                shops.stream().limit(MAX_MAP_SHOPS).toList(), shops.size() > MAX_MAP_SHOPS);
    }

    /**
     * 메인 화면 임박 빈자리. 지금부터 8시간 안에 시작하는 일반 시술 빈자리를 매장마다 가장 빠른 1건씩,
     * 빠른 시작순으로 최대 10개 돌려준다. 매장이 열지 않아도 영업시간 안 빈 시각이면 나온다
     * (빈 시각 판단은 B 의 가용 시간 계산: 영업시간 + 열기 − 닫기 − 예약, 예약 클릭 마감 반영).
     */
    public List<ExploreVacancyResponse> searchImminent() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, Item> earliestByShop = findBusinessHourItems(exploreVacancyQueryRepository.findImminentCandidates(),
                now, now.plus(BookingPolicy.IMMINENT_FROM), now)
                .collect(Collectors.toMap(item -> item.candidate().shopId(), item -> item,
                        ExploreVacancyService::earlier));
        return earliestByShop.values().stream()
                .sorted(IMMINENT_ORDER)
                .limit(IMMINENT_LIMIT)
                .map(item -> ExploreVacancyResponse.of(item.candidate(), item.startsAt(), item.quote()))
                .toList();
    }

    private static Item earlier(Item a, Item b) {
        return IMMINENT_ORDER.compare(a, b) <= 0 ? a : b;
    }

    // 필터를 검사하고 조건에 맞는 빈자리(시술 × 시작 시각)를 구한다. 정렬하지 않은 상태다
    private List<Item> filteredItems(ExploreVacancyFilter filter, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate lastDay = today.plusDays(BookingPolicy.BOOKING_MAX_DAYS - 1L);
        LocalDate startDate = filter.startDate() != null ? filter.startDate() : today;
        LocalDate endDate = filter.endDate() != null
                ? filter.endDate()
                : min(startDate.plusDays(DEFAULT_DAYS - 1L), lastDay);
        validateDates(startDate, endDate, lastDay);
        validateClosingWithinHours(filter.closingWithinHours());
        List<String> regionCodes = regionCodes(filter.regionCodes());
        String keyword = keyword(filter.keyword());
        MapBounds bounds = bounds(filter);
        // 지난 날짜는 어차피 예약할 수 없으므로 오늘부터 본다
        LocalDate fromDate = startDate.isBefore(today) ? today : startDate;
        if (fromDate.isAfter(endDate)) {
            return List.of();
        }
        ExploreVacancyCondition condition = new ExploreVacancyCondition(fromDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay(), regionCodes, filter.categoryId(), filter.shopId(),
                filter.treatmentType(), keyword,
                bounds);

        return candidateItems(filter.priceTier(), condition, now)
                .filter(item -> filter.timeOfDay() == null
                        || filter.timeOfDay().contains(item.startsAt().toLocalTime()))
                .filter(item -> filter.closingWithinHours() == null
                        || !bookingClosesAt(item).isAfter(now.plusHours(filter.closingWithinHours())))
                .filter(item -> filter.priceTier() == null
                        || item.quote().tier().compareTo(filter.priceTier()) >= 0)
                .toList();
    }

    // 타임세일(초임박)은 메인 임박 빈자리처럼 OPEN 없이 영업시간 안 빈 시각에서 찾고, 초임박가가 일반 판매가보다 싼 시술만 남긴다.
    // 3시간 범위는 뒤의 가격 구간 조건이 자르므로 여기서는 8시간까지만 본다. 그 밖의 조회는 OPEN 시간대 기준이다
    private Stream<Item> candidateItems(PriceTier priceTier, ExploreVacancyCondition condition, LocalDateTime now) {
        if (priceTier != PriceTier.LAST_MINUTE) {
            return findItems(exploreVacancyQueryRepository.findCandidates(condition), condition, now);
        }
        List<ExploreVacancyCandidate> discounted = exploreVacancyQueryRepository.findBusinessHourCandidates(condition)
                .stream()
                .filter(candidate -> candidate.lastMinutePrice() < candidate.price())
                .toList();
        return findBusinessHourItems(discounted, max(condition.from(), now),
                min(condition.to(), now.plus(BookingPolicy.IMMINENT_FROM)), now);
    }

    // 영업시간 기준: B 의 가용 시간 계산(영업시간 + 열기 − 닫기 − 예약, 예약 클릭 마감 반영)을 통과한 시각 중 [from, to) 만 남긴다.
    // 범위가 자정을 넘으면 다음 날 새벽 시각도 본다
    private Stream<Item> findBusinessHourItems(List<ExploreVacancyCandidate> candidates, LocalDateTime from,
            LocalDateTime to, LocalDateTime now) {
        Map<AvailabilityKey, List<LocalDateTime>> availableTimes = new HashMap<>();
        List<Item> items = new ArrayList<>();
        for (ExploreVacancyCandidate candidate : candidates) {
            for (LocalDate date = from.toLocalDate(); date.atStartOfDay().isBefore(to); date = date.plusDays(1)) {
                AvailabilityKey key = new AvailabilityKey(candidate.shopId(), date, candidate.durationMinutes());
                List<LocalDateTime> startTimes = availableTimes.computeIfAbsent(key,
                        k -> availabilityService.getAvailableStartTimes(k.shopId(), k.date(), k.durationMinutes()));
                for (LocalDateTime startsAt : startTimes) {
                    if (!startsAt.isBefore(from) && startsAt.isBefore(to)) {
                        items.add(new Item(candidate, startsAt, quote(candidate, startsAt, now)));
                    }
                }
            }
        }
        return items.stream();
    }

    // OPEN 시간대 안에서 시작하고 B 의 가용 시간 계산을 통과한 시각만 남긴다. 겹치는 OPEN 이 있어도 시술·시각당 한 번만 낸다
    private Stream<Item> findItems(List<ExploreVacancyCandidate> candidates, ExploreVacancyCondition condition,
            LocalDateTime now) {
        Map<AvailabilityKey, List<LocalDateTime>> availableTimes = new HashMap<>();
        Map<String, Item> items = new LinkedHashMap<>();
        for (ExploreVacancyCandidate candidate : candidates) {
            LocalDateTime from = max(candidate.openStartsAt(), condition.from());
            LocalDateTime to = min(candidate.openEndsAt(), condition.to());
            for (LocalDate date = from.toLocalDate(); date.atStartOfDay().isBefore(to); date = date.plusDays(1)) {
                AvailabilityKey key = new AvailabilityKey(candidate.shopId(), date, candidate.durationMinutes());
                List<LocalDateTime> startTimes = availableTimes.computeIfAbsent(key,
                        k -> availabilityService.getAvailableStartTimes(k.shopId(), k.date(), k.durationMinutes()));
                for (LocalDateTime startsAt : startTimes) {
                    if (!startsAt.isBefore(from) && startsAt.isBefore(to)) {
                        items.putIfAbsent(candidate.treatmentId() + "-" + startsAt,
                                new Item(candidate, startsAt, quote(candidate, startsAt, now)));
                    }
                }
            }
        }
        return items.values().stream();
    }

    // 매장 상세는 일반 시술만, 초보 시술 모집 상세는 초보 시술만 보여준다. 카드 값이 연결되는 상세와 맞도록
    // 일반 빈자리가 있으면 일반 빈자리로, 없으면 초보 시술 빈자리로 요약하고, 섞인 매장의 초보 시술 빈자리는 따로 담는다
    private static ExploreShopResponse toShop(List<Item> items) {
        ExploreVacancyCandidate shop = items.get(0).candidate();
        List<Item> modelItems = items.stream()
                .filter(item -> item.candidate().treatmentType() == TreatmentType.MODEL)
                .toList();
        boolean modelOnly = modelItems.size() == items.size();
        List<Item> linked = modelOnly
                ? items
                : items.stream().filter(item -> item.candidate().treatmentType() != TreatmentType.MODEL).toList();
        return new ExploreShopResponse(
                shop.shopId(),
                shop.shopName(),
                shop.regionCode(),
                shop.address(),
                shop.latitude(),
                shop.longitude(),
                shop.shopImageUrl(),
                linked.size(),
                minSalePrice(linked),
                linked.stream().mapToInt(item -> item.quote().discountRate()).max().orElseThrow(),
                TimeUtils.toOffset(linked.stream().map(Item::startsAt).min(LocalDateTime::compareTo).orElseThrow()),
                modelOnly,
                modelItems.size(),
                modelItems.isEmpty() ? null : minSalePrice(modelItems));
    }

    private static int minSalePrice(List<Item> items) {
        return items.stream().mapToInt(item -> item.quote().salePrice()).min().orElseThrow();
    }

    private static PriceQuote quote(ExploreVacancyCandidate candidate, LocalDateTime startsAt, LocalDateTime now) {
        return PriceCalculator.quote(candidate.regularPrice(),
                new SalePrices(candidate.price(), candidate.lastMinutePrice()), startsAt, now);
    }

    private static Comparator<Item> comparator(ExploreVacancySort sort) {
        return switch (Objects.requireNonNullElse(sort, ExploreVacancySort.START_ASC)) {
            case START_ASC -> TIEBREAK;
            case DISCOUNT_DESC -> Comparator.comparingInt((Item item) -> item.quote().discountRate())
                    .reversed().thenComparing(TIEBREAK);
            case PRICE_ASC -> Comparator.comparingInt((Item item) -> item.quote().salePrice())
                    .thenComparing(TIEBREAK);
        };
    }

    private static LocalDateTime bookingClosesAt(Item item) {
        return item.startsAt().minus(BookingPolicy.BOOKING_DEADLINE);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "page 는 0 이상, size 는 1~50 이어야 합니다.");
        }
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate, LocalDate lastDay) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "endDate 는 startDate 와 같거나 이후 날짜여야 합니다.");
        }
        if (endDate.isAfter(lastDay)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "오늘부터 60일 이내 날짜만 조회할 수 있습니다.");
        }
    }

    private static void validateClosingWithinHours(Integer hours) {
        if (hours != null && (hours < 1 || hours > MAX_CLOSING_WITHIN_HOURS)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "closingWithinHours 는 1~72 이어야 합니다.");
        }
    }

    private static List<String> regionCodes(List<String> regionCodes) {
        if (regionCodes == null) {
            return List.of();
        }
        List<String> codes = regionCodes.stream().filter(code -> !code.isBlank()).map(String::trim).distinct()
                .toList();
        if (codes.size() > MAX_REGION_CODES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "regionCode 는 10개까지 지정할 수 있습니다.");
        }
        return codes;
    }

    private static String keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "keyword 는 50자 이하여야 합니다.");
        }
        return trimmed;
    }

    // 지도 범위는 네 값을 함께 보내야 하며, 하나라도 빠지거나 최솟값이 최댓값 이상이면 잘못된 요청이다
    private static MapBounds bounds(ExploreVacancyFilter filter) {
        long given = Stream.of(filter.minLat(), filter.maxLat(), filter.minLng(), filter.maxLng())
                .filter(Objects::nonNull).count();
        if (given == 0) {
            return null;
        }
        if (given < 4 || filter.minLat().compareTo(filter.maxLat()) >= 0
                || filter.minLng().compareTo(filter.maxLng()) >= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "지도 범위는 minLat < maxLat, minLng < maxLng 네 값을 함께 보내야 합니다.");
        }
        return new MapBounds(filter.minLat(), filter.maxLat(), filter.minLng(), filter.maxLng());
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private record AvailabilityKey(Long shopId, LocalDate date, int durationMinutes) {
    }

    private record Item(ExploreVacancyCandidate candidate, LocalDateTime startsAt, PriceQuote quote) {
    }
}
