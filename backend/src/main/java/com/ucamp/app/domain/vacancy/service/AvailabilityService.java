package com.ucamp.app.domain.vacancy.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationOccupancy;
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

    private static final int MAX_IN_SIZE = 1000;

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
     * 여러 매장의 일정을 기간 전체로 한 번에 읽어 둔다 (C: 빈자리 탐색용). 매장·날짜마다 쿼리를 보내지 않으려는 것이다.
     * 잠금 없이 읽은 시점의 값이므로 조회 화면에만 쓴다. 예약 점유는 validateAvailable 로 다시 검사해야 한다.
     *
     * @param fromDate           조회할 첫 날짜
     * @param toDate             조회할 마지막 날짜 (포함)
     * @param maxDurationMinutes 조회할 시술 중 가장 긴 소요 시간. 자정을 넘는 시술의 일정까지 읽는 데 쓴다
     */
    public AvailabilitySnapshot loadSnapshot(Collection<Long> shopIds, LocalDate fromDate, LocalDate toDate,
            int maxDurationMinutes) {
        requirePositive(maxDurationMinutes);
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.plusDays(1).atStartOfDay().plusMinutes(maxDurationMinutes);
        List<ShopBusinessHour> hours = new ArrayList<>();
        List<ShopScheduleException> exceptions = new ArrayList<>();
        Map<Long, List<Occupancy>> occupanciesByShop = new HashMap<>();
        List<Long> ids = List.copyOf(new LinkedHashSet<>(shopIds));
        // Oracle 은 IN 목록을 1,000개까지만 받는다
        for (int start = 0; start < ids.size(); start += MAX_IN_SIZE) {
            List<Long> chunk = ids.subList(start, Math.min(start + MAX_IN_SIZE, ids.size()));
            hours.addAll(shopBusinessHourRepository.findByShopIdIn(chunk));
            exceptions.addAll(shopScheduleExceptionRepository.findOverlappingByShopIds(chunk, from, to));
            for (ReservationOccupancy row : reservationRepository.findActiveOccupancies(chunk, from, to)) {
                occupanciesByShop.computeIfAbsent(row.getShopId(), shopId -> new ArrayList<>()).add(new Occupancy(
                        row.getStatus(), row.getHoldExpiresAt(), row.getStartsAt(), row.getEndsAt()));
            }
        }
        return new AvailabilitySnapshot(
                hours.stream().collect(Collectors.groupingBy(ShopBusinessHour::getShopId)),
                exceptions.stream().collect(Collectors.groupingBy(ShopScheduleException::getShopId)),
                occupanciesByShop,
                LocalDateTime.now(clock));
    }

    /**
     * loadSnapshot 이 읽어 둔 일정. 시작 시각 계산은 getAvailableStartTimes 와 같고 DB 를 다시 읽지 않는다.
     */
    public static final class AvailabilitySnapshot {

        private final Map<Long, List<ShopBusinessHour>> hoursByShop;
        private final Map<Long, List<ShopScheduleException>> exceptionsByShop;
        private final Map<Long, List<Occupancy>> occupanciesByShop;
        private final LocalDateTime now;

        private AvailabilitySnapshot(Map<Long, List<ShopBusinessHour>> hoursByShop,
                Map<Long, List<ShopScheduleException>> exceptionsByShop,
                Map<Long, List<Occupancy>> occupanciesByShop, LocalDateTime now) {
            this.hoursByShop = hoursByShop;
            this.exceptionsByShop = exceptionsByShop;
            this.occupanciesByShop = occupanciesByShop;
            this.now = now;
        }

        public List<LocalDateTime> startTimes(Long shopId, LocalDate date, int durationMinutes) {
            requirePositive(durationMinutes);
            Duration duration = Duration.ofMinutes(durationMinutes);
            Schedule schedule = schedule(shopId, date, duration);
            return AvailabilityCalculator.availableStartTimes(
                    date, duration, schedule.openRanges(), schedule.blockedRanges(), now);
        }

        /**
         * 결제 대기 점유(HOLD) 때문에만 지금 시작할 수 없는 시각. 일부만 겹치는 시각도 담는다.
         * 메인 "이번 주 빈자리"처럼 결제가 끝나 예약이 확정되어야 줄어드는 집계에 쓴다.
         */
        public List<LocalDateTime> heldOnlyStartTimes(Long shopId, LocalDate date, int durationMinutes) {
            requirePositive(durationMinutes);
            Duration duration = Duration.ofMinutes(durationMinutes);
            Schedule schedule = schedule(shopId, date, duration);
            if (schedule.holdRanges().isEmpty()) {
                return List.of();
            }
            List<LocalDateTime> startTimes = AvailabilityCalculator.availableStartTimes(
                    date, duration, schedule.openRanges(), schedule.blockedRanges(), now);
            return AvailabilityCalculator.availableStartTimes(
                            date, duration, schedule.openRanges(), schedule.blockedRangesWithoutHolds(), now).stream()
                    .filter(start -> !startTimes.contains(start))
                    .toList();
        }

        private Schedule schedule(Long shopId, LocalDate date, Duration duration) {
            LocalDateTime from = date.atStartOfDay();
            LocalDateTime to = date.plusDays(1).atStartOfDay().plus(duration);
            List<ShopScheduleException> exceptions = exceptionsByShop.getOrDefault(shopId, List.of()).stream()
                    .filter(exception -> exception.getStartsAt().isBefore(to) && exception.getEndsAt().isAfter(from))
                    .toList();
            List<Occupancy> occupancies = occupanciesByShop.getOrDefault(shopId, List.of()).stream()
                    .filter(occupancy -> occupancy.startsAt().isBefore(to) && occupancy.endsAt().isAfter(from))
                    .toList();
            return toSchedule(hoursByShop.getOrDefault(shopId, List.of()), exceptions, occupancies, from, to, now);
        }
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

    /**
     * 기간 날짜마다 매장이 연 시간 (영업시간 ∪ 시간대 열기 − 시간대 닫기). 예약과 현재 시각은 보지 않는다
     * (매장 분석: 휴무일·영업 외 시간을 빼고 평균 내기용). 요일 영업시간은 지금 설정 기준이다.
     * 구간은 그 날짜 안으로 잘라 시간순으로 담고, 연 시간이 없는 날짜(휴무)는 담지 않는다.
     *
     * @param fromDate 첫 날짜
     * @param toDate   마지막 날짜 (포함)
     */
    public Map<LocalDate, List<TimeRange>> getOpenRangesByDate(Long shopId, LocalDate fromDate, LocalDate toDate) {
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.plusDays(1).atStartOfDay();
        Map<BusinessDay, ShopBusinessHour> hours = shopBusinessHourRepository.findByShopId(shopId).stream()
                .collect(Collectors.toMap(ShopBusinessHour::getDayOfWeek, Function.identity()));
        // 예외 일정을 걸친 날짜별로 잘라 둔다. 긴 기간도 날짜마다 예외 전체를 훑지 않으려는 것이다
        Map<LocalDate, List<TimeRange>> opened = new HashMap<>();
        Map<LocalDate, List<TimeRange>> closed = new HashMap<>();
        for (ShopScheduleException exception : shopScheduleExceptionRepository.findOverlapping(shopId, from, to)) {
            Map<LocalDate, List<TimeRange>> target =
                    exception.getExceptionType() == ScheduleExceptionType.OPEN ? opened : closed;
            LocalDate first = later(exception.getStartsAt().toLocalDate(), fromDate);
            LocalDate last = earlier(exception.getEndsAt().minusNanos(1).toLocalDate(), toDate);
            for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
                LocalDateTime start = later(exception.getStartsAt(), date.atStartOfDay());
                LocalDateTime end = earlier(exception.getEndsAt(), date.plusDays(1).atStartOfDay());
                if (start.isBefore(end)) {
                    target.computeIfAbsent(date, key -> new ArrayList<>()).add(new TimeRange(start, end));
                }
            }
        }
        Map<LocalDate, List<TimeRange>> result = new LinkedHashMap<>();
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            List<TimeRange> open = new ArrayList<>(opened.getOrDefault(date, List.of()));
            ShopBusinessHour hour = hours.get(BusinessDay.of(date));
            if (hour != null) {
                hour.rangeOn(date).ifPresent(open::add);
            }
            List<TimeRange> ranges = TimeRange.subtract(open, closed.getOrDefault(date, List.of()));
            if (!ranges.isEmpty()) {
                result.put(date, ranges);
            }
        }
        return result;
    }

    private static <T extends Comparable<? super T>> T later(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static <T extends Comparable<? super T>> T earlier(T a, T b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    // 시술 데이터가 잘못돼 0분 이하가 오면 구간을 만들 수 없어 500 이 되므로 업무 오류로 바꾼다
    private static void requirePositive(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_DURATION);
        }
    }

    private Schedule loadSchedule(Long shopId, LocalDateTime from, LocalDateTime to, LocalDateTime now) {
        List<Occupancy> occupancies = reservationRepository.findActiveOverlapping(shopId, from, to).stream()
                .map(reservation -> new Occupancy(reservation.getStatus(), reservation.getHoldExpiresAt(),
                        reservation.getStartsAt(), reservation.getEndsAt()))
                .toList();
        return toSchedule(shopBusinessHourRepository.findByShopId(shopId),
                shopScheduleExceptionRepository.findOverlapping(shopId, from, to), occupancies, from, to, now);
    }

    // 한 매장씩 읽을 때와 여러 매장을 묶어 읽을 때가 같은 규칙으로 계산하도록 한곳에 둔다
    private static Schedule toSchedule(List<ShopBusinessHour> businessHours, List<ShopScheduleException> exceptions,
            List<Occupancy> occupancies, LocalDateTime from, LocalDateTime to, LocalDateTime now) {
        Map<BusinessDay, ShopBusinessHour> hours = businessHours.stream()
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
        for (ShopScheduleException exception : exceptions) {
            TimeRange range = new TimeRange(exception.getStartsAt(), exception.getEndsAt());
            if (exception.getExceptionType() == ScheduleExceptionType.OPEN) {
                openRanges.add(range);
            } else {
                closedRanges.add(range);
            }
        }
        List<Occupancy> occupying = occupancies.stream()
                .filter(occupancy -> SalePolicy.isOccupying(occupancy.status(), occupancy.holdExpiresAt(), now))
                .toList();
        List<TimeRange> confirmedRanges = occupying.stream()
                .filter(occupancy -> occupancy.status() != ReservationStatus.HOLD)
                .map(occupancy -> new TimeRange(occupancy.startsAt(), occupancy.endsAt()))
                .toList();
        List<TimeRange> holdRanges = occupying.stream()
                .filter(occupancy -> occupancy.status() == ReservationStatus.HOLD)
                .map(occupancy -> new TimeRange(occupancy.startsAt(), occupancy.endsAt()))
                .toList();
        return new Schedule(businessRanges, openRanges, closedRanges, confirmedRanges, holdRanges);
    }

    private record Occupancy(ReservationStatus status, LocalDateTime holdExpiresAt, LocalDateTime startsAt,
            LocalDateTime endsAt) {
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
