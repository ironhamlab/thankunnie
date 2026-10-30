package com.ucamp.app.domain.vacancy.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.schedule.entity.BusinessDay;
import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;
import com.ucamp.app.domain.schedule.entity.ShopBusinessHour;
import com.ucamp.app.domain.schedule.entity.ShopScheduleException;
import com.ucamp.app.domain.schedule.repository.ShopBusinessHourRepository;
import com.ucamp.app.domain.schedule.repository.ShopScheduleExceptionRepository;
import com.ucamp.app.domain.vacancy.policy.AvailabilityCalculator;
import com.ucamp.app.domain.vacancy.policy.SalePolicy;
import com.ucamp.app.domain.vacancy.policy.TimeRange;

/**
 * 매장 가용 시간 조회 (B-06). 영업시간·시간대 열기·닫기·예약을 읽어 AvailabilityCalculator 로 계산한다.
 * 시술 소요 시간은 시술 엔티티가 생기기 전까지 호출하는 쪽이 넘긴다.
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private final ShopBusinessHourRepository shopBusinessHourRepository;
    private final ShopScheduleExceptionRepository shopScheduleExceptionRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public AvailabilityService(ShopBusinessHourRepository shopBusinessHourRepository,
            ShopScheduleExceptionRepository shopScheduleExceptionRepository,
            ReservationRepository reservationRepository, Clock clock) {
        this.shopBusinessHourRepository = shopBusinessHourRepository;
        this.shopScheduleExceptionRepository = shopScheduleExceptionRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    /**
     * 날짜에 시술을 시작할 수 있는 시각 목록 (C: 검색·상세 화면용).
     */
    public List<LocalDateTime> getAvailableStartTimes(Long shopId, LocalDate date, int durationMinutes) {
        requirePositive(durationMinutes);
        Duration duration = Duration.ofMinutes(durationMinutes);
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay().plus(duration);
        LocalDateTime now = LocalDateTime.now(clock);
        Schedule schedule = loadSchedule(shopId, from, to, now);
        return AvailabilityCalculator.availableStartTimes(
                date, duration, schedule.openRanges(), schedule.blockedRanges(), now);
    }

    /**
     * 시술을 이 시각에 시작할 수 있는지 검사한다 (D: 예약 점유용).
     * 같은 시간 동시 예약을 막으려면 ShopLock 으로 매장을 잠근 뒤 호출하고 같은 트랜잭션에서 저장한다.
     */
    public void validateAvailable(Long shopId, LocalDateTime startsAt, int durationMinutes) {
        requirePositive(durationMinutes);
        LocalDateTime now = LocalDateTime.now(clock);
        SalePolicy.validateBookable(startsAt, now);
        TimeRange range = new TimeRange(startsAt, startsAt.plusMinutes(durationMinutes));
        Schedule schedule = loadSchedule(shopId, range.start(), range.end(), now);
        if (schedule.reservedRanges().stream().anyMatch(range::overlaps)) {
            throw new BusinessException(ErrorCode.VACANCY_TIME_TAKEN);
        }
        if (!AvailabilityCalculator.isFree(range, schedule.openRanges(), schedule.closedRanges())) {
            throw new BusinessException(ErrorCode.VACANCY_NOT_OPEN_TIME);
        }
    }

    /**
     * 고객이 지금 이 시각에 시술을 예약할 수 있는지 판단한다 (F: 알림 재공개·임박 판단용).
     * validateAvailable 과 같은 기준(클릭 마감·30분 단위·60일 + 영업시간·열기 안 + 닫기·예약과 안 겹침)이고,
     * 예외 대신 true/false 로 알려준다. 매장을 잠그지 않으므로 판단 직후 다른 예약이 들어올 수 있다.
     */
    public boolean isAvailable(Long shopId, LocalDateTime startsAt, int durationMinutes) {
        requirePositive(durationMinutes);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!SalePolicy.isBookable(startsAt, now)) {
            return false;
        }
        TimeRange range = new TimeRange(startsAt, startsAt.plusMinutes(durationMinutes));
        Schedule schedule = loadSchedule(shopId, range.start(), range.end(), now);
        return AvailabilityCalculator.isFree(range, schedule.openRanges(), schedule.blockedRanges());
    }

    /**
     * 날짜에 영업시간·시간대 열기로 열려 있고 닫기로 막히지 않은 시간이 하루 중 조금이라도 있는지 판단한다
     * (사장님 대시보드: 휴무와 만석 구분용). 예약과 현재 시각은 보지 않으며 30분 단위로 확인한다.
     */
    public boolean hasOpenTime(Long shopId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        Schedule schedule = loadSchedule(shopId, from, to, LocalDateTime.now(clock));
        for (LocalDateTime start = from; start.isBefore(to);
                start = start.plusMinutes(BookingPolicy.TIME_UNIT_MINUTES)) {
            TimeRange unit = new TimeRange(start, start.plusMinutes(BookingPolicy.TIME_UNIT_MINUTES));
            if (AvailabilityCalculator.isFree(unit, schedule.openRanges(), schedule.closedRanges())) {
                return true;
            }
        }
        return false;
    }

    // 시술 데이터가 잘못돼 0분 이하가 오면 구간을 만들 수 없어 500 이 되므로 업무 오류로 바꾼다
    private static void requirePositive(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_DURATION);
        }
    }

    private Schedule loadSchedule(Long shopId, LocalDateTime from, LocalDateTime to, LocalDateTime now) {
        Map<BusinessDay, ShopBusinessHour> hours = shopBusinessHourRepository.findByShopId(shopId).stream()
                .collect(Collectors.toMap(ShopBusinessHour::getDayOfWeek, Function.identity()));
        List<TimeRange> openRanges = new ArrayList<>();
        for (LocalDate date = from.toLocalDate(); !date.isAfter(to.toLocalDate()); date = date.plusDays(1)) {
            ShopBusinessHour hour = hours.get(BusinessDay.of(date));
            if (hour != null) {
                hour.rangeOn(date).ifPresent(openRanges::add);
            }
        }
        List<TimeRange> closedRanges = new ArrayList<>();
        for (ShopScheduleException exception : shopScheduleExceptionRepository.findOverlapping(shopId, from, to)) {
            TimeRange range = new TimeRange(exception.getStartsAt(), exception.getEndsAt());
            if (exception.getExceptionType() == ScheduleExceptionType.OPEN) {
                openRanges.add(range);
            } else {
                closedRanges.add(range);
            }
        }
        List<TimeRange> reservedRanges = reservationRepository.findActiveOverlapping(shopId, from, to).stream()
                .filter(reservation -> SalePolicy.isOccupying(
                        reservation.getStatus(), reservation.getHoldExpiresAt(), now))
                .map(reservation -> new TimeRange(reservation.getStartsAt(), reservation.getEndsAt()))
                .toList();
        return new Schedule(openRanges, closedRanges, reservedRanges);
    }

    private record Schedule(List<TimeRange> openRanges, List<TimeRange> closedRanges,
            List<TimeRange> reservedRanges) {

        List<TimeRange> blockedRanges() {
            List<TimeRange> blocked = new ArrayList<>(closedRanges);
            blocked.addAll(reservedRanges);
            return blocked;
        }
    }
}
