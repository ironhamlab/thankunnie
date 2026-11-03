package com.ucamp.app.domain.schedule.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.ShopLock;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.schedule.dto.request.BusinessHourSaveRequest;
import com.ucamp.app.domain.schedule.dto.response.BusinessHourResponse;
import com.ucamp.app.domain.schedule.entity.BusinessDay;
import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;
import com.ucamp.app.domain.schedule.entity.ShopBusinessHour;
import com.ucamp.app.domain.schedule.repository.ShopBusinessHourRepository;
import com.ucamp.app.domain.schedule.repository.ShopScheduleExceptionRepository;
import com.ucamp.app.domain.vacancy.policy.AvailabilityCalculator;
import com.ucamp.app.domain.vacancy.policy.SalePolicy;
import com.ucamp.app.domain.vacancy.policy.TimeRange;

/**
 * 요일별 영업시간 관리 (B-02). 매장 본인 매장만 다룬다.
 * 영업시간을 줄여 기존 예약이 열린 시간 밖으로 밀려나면 차단한다.
 */
@Service
@Transactional(readOnly = true)
public class ShopBusinessHourService {

    private final ShopBusinessHourRepository shopBusinessHourRepository;
    private final ShopScheduleExceptionRepository shopScheduleExceptionRepository;
    private final ReservationRepository reservationRepository;
    private final ShopLock shopLock;
    private final Clock clock;

    public ShopBusinessHourService(ShopBusinessHourRepository shopBusinessHourRepository,
            ShopScheduleExceptionRepository shopScheduleExceptionRepository,
            ReservationRepository reservationRepository, ShopLock shopLock, Clock clock) {
        this.shopBusinessHourRepository = shopBusinessHourRepository;
        this.shopScheduleExceptionRepository = shopScheduleExceptionRepository;
        this.reservationRepository = reservationRepository;
        this.shopLock = shopLock;
        this.clock = clock;
    }

    /**
     * 본인 매장의 월~일 영업시간. 저장하지 않은 요일은 휴무로 보여준다.
     */
    public List<BusinessHourResponse> getBusinessHours(AuthUser loginUser) {
        loginUser.requireRole(UserRole.OWNER);
        return toResponses(findHoursByDay(loginUser.shopId()));
    }

    /**
     * 본인 매장의 월~일 영업시간을 한 번에 저장한다.
     */
    @Transactional
    public List<BusinessHourResponse> updateBusinessHours(AuthUser loginUser, BusinessHourSaveRequest request) {
        loginUser.requireRole(UserRole.OWNER);
        Long shopId = loginUser.shopId();
        validateDays(request.days());

        shopLock.lock(shopId);
        Map<BusinessDay, ShopBusinessHour> hours = findHoursByDay(shopId);
        for (BusinessHourSaveRequest.Day day : request.days()) {
            ShopBusinessHour hour = hours.get(day.dayOfWeek());
            if (hour == null) {
                hours.put(day.dayOfWeek(), shopBusinessHourRepository.save(ShopBusinessHour.builder()
                        .shopId(shopId)
                        .dayOfWeek(day.dayOfWeek())
                        .closed(day.closed())
                        .openTime(day.openTime())
                        .closeTime(day.closeTime())
                        .build()));
            } else {
                hour.change(day.closed(), day.openTime(), day.closeTime());
            }
        }
        requireNoStrandedReservation(shopId, hours);
        return toResponses(hours);
    }

    private static void validateDays(List<BusinessHourSaveRequest.Day> days) {
        Set<BusinessDay> dayOfWeeks = EnumSet.noneOf(BusinessDay.class);
        days.forEach(day -> dayOfWeeks.add(day.dayOfWeek()));
        if (days.size() != BusinessDay.values().length || dayOfWeeks.size() != BusinessDay.values().length) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_BUSINESS_DAYS);
        }
        for (BusinessHourSaveRequest.Day day : days) {
            if (day.closed()) {
                continue;
            }
            if (day.openTime() == null || day.closeTime() == null) {
                throw new BusinessException(ErrorCode.VACANCY_BUSINESS_TIME_REQUIRED);
            }
            if (!isOnTimeUnit(day.openTime()) || !isOnTimeUnit(day.closeTime())) {
                throw new BusinessException(ErrorCode.VACANCY_INVALID_TIME_UNIT);
            }
            if (!day.closeTime().isAfter(day.openTime())) {
                throw new BusinessException(ErrorCode.VACANCY_INVALID_TIME_RANGE);
            }
        }
    }

    /**
     * 앞으로의 예약 중 새 영업시간(+ 시간대 열기)으로 덮이지 않는 예약이 있으면 차단한다.
     */
    private void requireNoStrandedReservation(Long shopId, Map<BusinessDay, ShopBusinessHour> hours) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime until = now.toLocalDate().plusDays(BookingPolicy.BOOKING_MAX_DAYS + 1L).atStartOfDay();
        List<TimeRange> openExceptions = shopScheduleExceptionRepository.findOverlapping(shopId, now, until).stream()
                .filter(exception -> exception.getExceptionType() == ScheduleExceptionType.OPEN)
                .map(exception -> new TimeRange(exception.getStartsAt(), exception.getEndsAt()))
                .toList();
        boolean stranded = reservationRepository.findActiveOverlapping(shopId, now, until).stream()
                .filter(reservation -> SalePolicy.isOccupying(
                        reservation.getStatus(), reservation.getHoldExpiresAt(), now))
                .anyMatch(reservation -> !isOpen(reservation, hours, openExceptions));
        if (stranded) {
            throw new BusinessException(ErrorCode.VACANCY_RESERVED_TIME);
        }
    }

    private static boolean isOpen(Reservation reservation, Map<BusinessDay, ShopBusinessHour> hours,
            List<TimeRange> openExceptions) {
        List<TimeRange> openRanges = new ArrayList<>(openExceptions);
        LocalDate endDate = reservation.getEndsAt().toLocalDate();
        for (LocalDate date = reservation.getStartsAt().toLocalDate(); !date.isAfter(endDate);
                date = date.plusDays(1)) {
            ShopBusinessHour hour = hours.get(BusinessDay.of(date));
            if (hour != null) {
                hour.rangeOn(date).ifPresent(openRanges::add);
            }
        }
        TimeRange range = new TimeRange(reservation.getStartsAt(), reservation.getEndsAt());
        return AvailabilityCalculator.isFree(range, openRanges, List.of());
    }

    private Map<BusinessDay, ShopBusinessHour> findHoursByDay(Long shopId) {
        Map<BusinessDay, ShopBusinessHour> hours = new EnumMap<>(BusinessDay.class);
        shopBusinessHourRepository.findByShopId(shopId).forEach(hour -> hours.put(hour.getDayOfWeek(), hour));
        return hours;
    }

    private static List<BusinessHourResponse> toResponses(Map<BusinessDay, ShopBusinessHour> hours) {
        return Arrays.stream(BusinessDay.values())
                .map(day -> hours.containsKey(day)
                        ? BusinessHourResponse.from(hours.get(day))
                        : BusinessHourResponse.closedDay(day))
                .toList();
    }

    private static boolean isOnTimeUnit(LocalTime time) {
        return time.getMinute() % BookingPolicy.TIME_UNIT_MINUTES == 0 && time.getSecond() == 0
                && time.getNano() == 0;
    }
}
