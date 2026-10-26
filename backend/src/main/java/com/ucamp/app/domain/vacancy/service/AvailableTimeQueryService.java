package com.ucamp.app.domain.vacancy.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.vacancy.dto.response.AvailableTimesResponse;
import com.ucamp.app.domain.vacancy.repository.ShopStatusQueryRepository;

/**
 * 가용 시간 공용 조회 (B-06). 사장님 열기·닫기 캘린더(B-08)와 고객 상세 시간 선택(C)이 함께 쓴다.
 * 계산은 AvailabilityService 에 맡기고, 여기서는 매장·날짜·소요 시간 입력만 검사한다.
 */
@Service
@Transactional(readOnly = true)
public class AvailableTimeQueryService {

    // 하루를 넘는 시술은 없으므로 그보다 큰 값은 잘못된 요청으로 본다
    private static final int MAX_DURATION_MINUTES = 24 * 60;

    private final AvailabilityService availabilityService;
    private final ShopStatusQueryRepository shopStatusQueryRepository;
    private final Clock clock;

    public AvailableTimeQueryService(AvailabilityService availabilityService,
            ShopStatusQueryRepository shopStatusQueryRepository, Clock clock) {
        this.availabilityService = availabilityService;
        this.shopStatusQueryRepository = shopStatusQueryRepository;
        this.clock = clock;
    }

    /**
     * 날짜에 시술을 시작할 수 있는 시각 목록. 없는 매장·운영 중지 매장은 404, 오늘~60일 밖 날짜는 400.
     */
    public AvailableTimesResponse getAvailableTimes(Long shopId, LocalDate date, int durationMinutes) {
        if (durationMinutes <= 0 || durationMinutes > MAX_DURATION_MINUTES) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_DURATION);
        }
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today) || date.isAfter(today.plusDays(BookingPolicy.BOOKING_MAX_DAYS - 1L))) {
            throw new BusinessException(ErrorCode.VACANCY_OUT_OF_RANGE);
        }
        if (!shopStatusQueryRepository.isActive(shopId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<OffsetDateTime> startTimes = availabilityService
                .getAvailableStartTimes(shopId, date, durationMinutes).stream()
                .map(TimeUtils::toOffset)
                .toList();
        return new AvailableTimesResponse(shopId, date, durationMinutes, startTimes);
    }
}
