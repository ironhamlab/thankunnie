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
import com.ucamp.app.domain.auth.entity.ShopAccountStatus;
import com.ucamp.app.domain.auth.repository.ShopAccountRepository;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
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
    private final ShopAccountRepository shopAccountRepository;
    private final Clock clock;

    public AvailabilityService(ShopBusinessHourRepository shopBusinessHourRepository,
            ShopScheduleExceptionRepository shopScheduleExceptionRepository,
            ReservationRepository reservationRepository, ShopAccountRepository shopAccountRepository, Clock clock) {
        this.shopBusinessHourRepository = shopBusinessHourRepository;
        this.shopScheduleExceptionRepository = shopScheduleExceptionRepository;
        this.reservationRepository = reservationRepository;
        this.shopAccountRepository = shopAccountRepository;
        this.clock = clock;
    }

    /**
     * 날짜에 시술을 시작할 수 있는 시각 목록 (C: 검색·상세 화면용).
     */
    public List<LocalDateTime> getAvailableStartTimes(Long shopId, LocalDate date, int durationMinutes) {
        return getDayAvailability(shopId, date, durationMinutes).startTimes();
    }

    /**
     * 날짜에 시술을 시작할 수 있는 시각과, 그중 영업시간만으로는 안 되고 매장이 연 시간 덕분에 되는 시각.
     * 시술이 영업시간 밖으로 조금이라도 넘어가면 연장 영업 시각이다 (휴무일에 연 시간은 모두 해당).
     * 고객 상세 화면이 연장 영업임을 표시하는 데 쓴다.
     */
    public DayAvailability getDayAvailability(Long shopId, LocalDate date, int durationMinutes) {
        requirePositive(durationMinutes);
        Duration duration = Duration.ofMinutes(durationMinutes);
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay().plus(duration);
        LocalDateTime now = LocalDateTime.now(clock);
        Schedule schedule = loadSchedule(shopId, from, to, now);
        List<LocalDateTime> startTimes = AvailabilityCalculator.availableStartTimes(
                date, duration, schedule.openRanges(), schedule.blockedRanges(), now);
        List<LocalDateTime> extendedStartTimes = startTimes.stream()
                .filter(start -> !AvailabilityCalculator.isFree(
                        new TimeRange(start, start.plus(duration)), schedule.businessRanges(), List.of()))
                .toList();
        // 시간이 정확히 같은 점유 하나만 막고 있는 시각. 점유가 결제 없이 풀리면 그 시간이 그대로 비므로 예약 대기열에 설 수 있다.
        // 일부만 겹치는 시각은 대기열이 순서를 지켜 줄 수 없어 대기 대신 예약 불가로 둔다
        List<LocalDateTime> heldStartTimes = AvailabilityCalculator.availableStartTimes(
                        date, duration, schedule.openRanges(), schedule.blockedRangesWithoutHolds(), now).stream()
                .filter(start -> !startTimes.contains(start))
                .filter(start -> schedule.isExactlyHeld(new TimeRange(start, start.plus(duration))))
                .toList();
        return new DayAvailability(startTimes, extendedStartTimes, heldStartTimes);
    }

    /**
     * @param startTimes         시작 가능 시각, 오름차순
     * @param extendedStartTimes startTimes 중 영업시간 밖 연장 영업이 있어야 가능한 시각, 오름차순
     * @param heldStartTimes     다른 고객의 결제 대기 점유(HOLD) 때문에만 지금 시작할 수 없는 시각, 오름차순.
     *                           startTimes 와 겹치지 않는다
     */
    public record DayAvailability(List<LocalDateTime> startTimes, List<LocalDateTime> extendedStartTimes,
            List<LocalDateTime> heldStartTimes) {

        public DayAvailability(List<LocalDateTime> startTimes, List<LocalDateTime> extendedStartTimes) {
            this(startTimes, extendedStartTimes, List.of());
        }
    }

    /**
     * 시술을 이 시각에 시작할 수 있는지 검사한다 (D: 예약 점유용).
     * 같은 시간 동시 예약을 막으려면 ShopLock 으로 매장을 잠근 뒤 호출하고 같은 트랜잭션에서 저장한다.
     */
    public void validateAvailable(Long shopId, LocalDateTime startsAt, int durationMinutes) {
        requirePositive(durationMinutes);
        LocalDateTime now = LocalDateTime.now(clock);
        SalePolicy.validateBookable(startsAt, now);
        // 화면을 열어 둔 사이 승인이 취소된 매장에 예약이 들어가지 않게 한다. 잠금 뒤에 읽어 승인 취소와 순서가 맞는다
        if (!shopAccountRepository.existsByShopIdAndStatus(shopId, ShopAccountStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.VACANCY_NOT_OPEN_TIME);
        }
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
     * 지금은 예약할 수 없지만, 막고 있는 것이 다른 고객의 결제 대기 점유(HOLD)뿐인지 판단한다 (예약 대기열용).
     * 점유가 결제 없이 풀리면 예약할 수 있으므로 화면은 예약하기 대신 대기하기를 보여준다.
     * 확정된 예약·닫힌 시간·영업 외 시간과 겹치면 기다려도 소용없으므로 false 다. 지금 바로 예약할 수 있어도 false 다.
     * 점유와 시작·끝 시각이 정확히 같을 때만 true 다. 일부만 겹치면 옆 시간 예약에 밀려 기다려도 못 잡을 수 있어 false 다.
     */
    public boolean isHeldOnly(Long shopId, LocalDateTime startsAt, int durationMinutes) {
        requirePositive(durationMinutes);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!SalePolicy.isBookable(startsAt, now)) {
            return false;
        }
        TimeRange range = new TimeRange(startsAt, startsAt.plusMinutes(durationMinutes));
        Schedule schedule = loadSchedule(shopId, range.start(), range.end(), now);
        return !AvailabilityCalculator.isFree(range, schedule.openRanges(), schedule.blockedRanges())
                && AvailabilityCalculator.isFree(range, schedule.openRanges(), schedule.blockedRangesWithoutHolds())
                && schedule.isExactlyHeld(range);
    }

    /**
     * 날짜에 영업시간·시간대 열기로 열려 있고 닫기로 막히지 않은 시간이 하루 중 조금이라도 있는지 판단한다
     * (매장 대시보드: 휴무와 만석 구분용). 예약과 현재 시각은 보지 않으며 30분 단위로 확인한다.
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
        List<TimeRange> businessRanges = new ArrayList<>();
        for (LocalDate date = from.toLocalDate(); !date.isAfter(to.toLocalDate()); date = date.plusDays(1)) {
            ShopBusinessHour hour = hours.get(BusinessDay.of(date));
            if (hour != null) {
                hour.rangeOn(date).ifPresent(businessRanges::add);
            }
        }
        List<TimeRange> openRanges = new ArrayList<>(businessRanges);
        List<TimeRange> closedRanges = new ArrayList<>();
        for (ShopScheduleException exception : shopScheduleExceptionRepository.findOverlapping(shopId, from, to)) {
            TimeRange range = new TimeRange(exception.getStartsAt(), exception.getEndsAt());
            if (exception.getExceptionType() == ScheduleExceptionType.OPEN) {
                openRanges.add(range);
            } else {
                closedRanges.add(range);
            }
        }
        List<Reservation> occupying = reservationRepository.findActiveOverlapping(shopId, from, to).stream()
                .filter(reservation -> SalePolicy.isOccupying(
                        reservation.getStatus(), reservation.getHoldExpiresAt(), now))
                .toList();
        List<TimeRange> confirmedRanges = occupying.stream()
                .filter(reservation -> reservation.getStatus() != ReservationStatus.HOLD)
                .map(reservation -> new TimeRange(reservation.getStartsAt(), reservation.getEndsAt()))
                .toList();
        List<TimeRange> holdRanges = occupying.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.HOLD)
                .map(reservation -> new TimeRange(reservation.getStartsAt(), reservation.getEndsAt()))
                .toList();
        return new Schedule(businessRanges, openRanges, closedRanges, confirmedRanges, holdRanges);
    }

    /**
     * @param businessRanges 요일 영업시간만의 구간 (연장 영업 판단용)
     * @param openRanges     영업시간과 매장이 연 시간을 합친 구간
     * @param confirmedRanges 결제 대기 점유가 아닌 예약(CONFIRMED·CANCEL_PENDING) 구간
     * @param holdRanges      유효한 결제 대기 점유(HOLD) 구간
     */
    private record Schedule(List<TimeRange> businessRanges, List<TimeRange> openRanges,
            List<TimeRange> closedRanges, List<TimeRange> confirmedRanges, List<TimeRange> holdRanges) {

        List<TimeRange> reservedRanges() {
            List<TimeRange> reserved = new ArrayList<>(confirmedRanges);
            reserved.addAll(holdRanges);
            return reserved;
        }

        List<TimeRange> blockedRanges() {
            List<TimeRange> blocked = new ArrayList<>(closedRanges);
            blocked.addAll(reservedRanges());
            return blocked;
        }

        /** 구간과 겹치는 점유가 하나뿐이고 시작·끝 시각이 구간과 똑같은지 */
        boolean isExactlyHeld(TimeRange range) {
            List<TimeRange> overlapping = holdRanges.stream().filter(range::overlaps).toList();
            return overlapping.size() == 1 && overlapping.get(0).equals(range);
        }

        // 결제 대기 점유가 모두 결제 없이 풀렸다고 볼 때 막히는 구간
        List<TimeRange> blockedRangesWithoutHolds() {
            List<TimeRange> blocked = new ArrayList<>(closedRanges);
            blocked.addAll(confirmedRanges);
            return blocked;
        }
    }
}
