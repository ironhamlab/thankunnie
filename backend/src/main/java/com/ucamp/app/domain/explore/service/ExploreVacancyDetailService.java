package com.ucamp.app.domain.explore.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.domain.explore.dto.response.ExploreNotBookableReason;
import com.ucamp.app.domain.explore.dto.response.ExploreVacancyDetailResponse;
import com.ucamp.app.domain.explore.repository.ExploreVacancyDetailCandidate;
import com.ucamp.app.domain.explore.repository.ExploreVacancyDetailRepository;
import com.ucamp.app.domain.vacancy.policy.PriceCalculator;
import com.ucamp.app.domain.vacancy.policy.PriceQuote;
import com.ucamp.app.domain.vacancy.policy.SalePolicy;
import com.ucamp.app.domain.vacancy.policy.SalePrices;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

/**
 * 고객 빈자리 상세 (C-03). 고른 시술 × 시작 시각의 예약 가능 여부와 가격을 조회 시점에 다시 계산한다.
 * 예약 가능 판단과 가격은 B 의 SalePolicy·AvailabilityService·PriceCalculator 를 그대로 쓴다.
 * 이미 지났거나 마감된 시각도 오류가 아니라 bookable=false 로 알린다.
 */
@Service
@Transactional(readOnly = true)
public class ExploreVacancyDetailService {

    private final ExploreVacancyDetailRepository exploreVacancyDetailRepository;
    private final AvailabilityService availabilityService;
    private final Clock clock;

    public ExploreVacancyDetailService(ExploreVacancyDetailRepository exploreVacancyDetailRepository,
            AvailabilityService availabilityService, Clock clock) {
        this.exploreVacancyDetailRepository = exploreVacancyDetailRepository;
        this.availabilityService = availabilityService;
        this.clock = clock;
    }

    /**
     * 빈자리 상세를 조회한다.
     *
     * @param treatmentId 시술 ID
     * @param startsAt    시작 시각 (KST)
     * @return 빈자리 상세
     */
    public ExploreVacancyDetailResponse getDetail(Long treatmentId, LocalDateTime startsAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        validateStartsAt(startsAt, now.toLocalDate());
        ExploreVacancyDetailCandidate candidate = exploreVacancyDetailRepository.findDetail(treatmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TREATMENT_NOT_FOUND));

        PriceQuote quote = PriceCalculator.quote(candidate.regularPrice(),
                new SalePrices(candidate.price(), candidate.lastMinutePrice()), startsAt, now);
        return ExploreVacancyDetailResponse.of(candidate, startsAt, quote,
                notBookableReason(candidate, startsAt, now));
    }

    // 시각 단위·범위는 검사를 마쳤으므로 isBookable 이 거짓이면 클릭 마감이 지난 것이다
    private ExploreNotBookableReason notBookableReason(ExploreVacancyDetailCandidate candidate,
            LocalDateTime startsAt, LocalDateTime now) {
        if (!SalePolicy.isBookable(startsAt, now)) {
            return ExploreNotBookableReason.BOOKING_CLOSED;
        }
        if (!availabilityService.isAvailable(candidate.shopId(), startsAt, candidate.durationMinutes())) {
            return availabilityService.isHeldOnly(candidate.shopId(), startsAt, candidate.durationMinutes())
                    ? ExploreNotBookableReason.HELD
                    : ExploreNotBookableReason.NOT_AVAILABLE;
        }
        return null;
    }

    private static void validateStartsAt(LocalDateTime startsAt, LocalDate today) {
        boolean onTimeUnit = startsAt.getMinute() % BookingPolicy.TIME_UNIT_MINUTES == 0
                && startsAt.getSecond() == 0
                && startsAt.getNano() == 0;
        if (!onTimeUnit) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_TIME_UNIT);
        }
        LocalDate lastDay = today.plusDays(BookingPolicy.BOOKING_MAX_DAYS - 1L);
        LocalDate date = startsAt.toLocalDate();
        if (date.isBefore(today) || date.isAfter(lastDay)) {
            throw new BusinessException(ErrorCode.VACANCY_OUT_OF_RANGE);
        }
    }
}
