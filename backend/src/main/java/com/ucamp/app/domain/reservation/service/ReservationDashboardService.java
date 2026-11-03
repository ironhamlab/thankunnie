package com.ucamp.app.domain.reservation.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.reservation.dto.response.ReservationDashboardResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationPeriodSummary;
import com.ucamp.app.domain.reservation.dto.response.ReservationStatsResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationStatsResponse.Granularity;
import com.ucamp.app.domain.reservation.repository.ReservationDashboardRepository;
import com.ucamp.app.domain.reservation.repository.ReservationDashboardRow;
import com.ucamp.app.domain.shop.policy.CustomerSegment;
import com.ucamp.app.domain.vacancy.policy.PriceTier;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

/**
 * 매장 홈 대시보드(오늘)와 매출·예약 분석. 매장은 로그인한 매장 계정으로 정해진다.
 * 금액은 예약 시점 적용가 기준이며, 종료 시각이 지난 확정 예약을 이용 추정으로 본다.
 */
@Service
@Transactional(readOnly = true)
public class ReservationDashboardService {

    /** 분석 최대 조회 기간: 약 10년. "전체" 조회도 이 안에서 처리한다 */
    private static final int MAX_STATS_DAYS = 3660;

    /** 이 일수까지는 날짜별, 넘으면 월별 추이 */
    private static final int DAILY_TREND_MAX_DAYS = 62;

    private final ReservationDashboardRepository reservationDashboardRepository;
    private final AvailabilityService availabilityService;
    private final Clock clock;

    public ReservationDashboardService(ReservationDashboardRepository reservationDashboardRepository,
            AvailabilityService availabilityService, Clock clock) {
        this.reservationDashboardRepository = reservationDashboardRepository;
        this.availabilityService = availabilityService;
        this.clock = clock;
    }

    public ReservationDashboardResponse getOwnerDashboard(AuthUser loginUser) {
        loginUser.requireRole(UserRole.OWNER);
        Long shopId = loginUser.shopId();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate monthFrom = today.withDayOfMonth(1);
        LocalDate previousFrom = monthFrom.minusMonths(1);
        LocalDate previousTo = previousFrom.withDayOfMonth(
                Math.min(today.getDayOfMonth(), previousFrom.lengthOfMonth()));

        List<ReservationDashboardRow> rows = reservationDashboardRepository.findRows(
                shopId, previousFrom.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay());
        List<ReservationDashboardRow> todayConfirmed = rows.stream()
                .filter(row -> row.confirmed() && row.startsAt().toLocalDate().equals(today))
                .toList();
        int upcoming = (int) todayConfirmed.stream().filter(row -> row.startsAt().isAfter(now)).count();
        int tomorrowConfirmed = (int) rows.stream()
                .filter(row -> row.confirmed() && row.startsAt().toLocalDate().equals(tomorrow))
                .count();

        // 최소 시간 단위(30분)로 계산한 "지금 예약받을 수 있는 시간". 시술별 소요 시간은 고려하지 않는다
        List<LocalDateTime> availableStartTimes =
                availabilityService.getAvailableStartTimes(shopId, today, BookingPolicy.TIME_UNIT_MINUTES);
        boolean closed = !availabilityService.hasOpenTime(shopId, today);

        long completedAmount = completedAmount(rows, monthFrom, today, now);
        long previousCompletedAmount = completedAmount(rows, previousFrom, previousTo, now);

        return new ReservationDashboardResponse(
                today,
                new ReservationDashboardResponse.Today(
                        todayConfirmed.size(),
                        upcoming,
                        closed,
                        (long) availableStartTimes.size() * BookingPolicy.TIME_UNIT_MINUTES,
                        toRanges(availableStartTimes)),
                tomorrowConfirmed,
                new ReservationDashboardResponse.MonthToDate(
                        monthFrom,
                        today,
                        completedAmount,
                        previousFrom,
                        previousTo,
                        previousCompletedAmount,
                        changeRate(completedAmount, previousCompletedAmount)),
                reservationDashboardRepository.countFavoriteCustomers(shopId));
    }

    /**
     * 매출·예약 분석. from·to 는 시술일 기준이며 둘 다 포함한다.
     * all 이면 서비스 이용 시작일~오늘이다. 아니면 둘 다 생략 시 이번 달 1일~말일,
     * from 만 생략하면 to 가 속한 달 1일부터, to 만 생략하면 from 이 속한 달 말일까지다.
     */
    public ReservationStatsResponse getOwnerStats(AuthUser loginUser, LocalDate from, LocalDate to, boolean all) {
        loginUser.requireRole(UserRole.OWNER);
        Long shopId = loginUser.shopId();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDate serviceStart = serviceStartDate(shopId, today);

        LocalDate start;
        LocalDate end;
        if (all) {
            start = serviceStart;
            end = today;
        } else if (from == null && to == null) {
            start = today.withDayOfMonth(1);
            end = today.withDayOfMonth(today.lengthOfMonth());
        } else if (from == null) {
            start = to.withDayOfMonth(1);
            end = to;
        } else if (to == null) {
            start = from;
            end = from.withDayOfMonth(from.lengthOfMonth());
        } else {
            start = from;
            end = to;
        }
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "to 는 from 과 같거나 이후 날짜여야 합니다.");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_STATS_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간은 최대 " + MAX_STATS_DAYS + "일입니다.");
        }
        Granularity granularity = days <= DAILY_TREND_MAX_DAYS ? Granularity.DAY : Granularity.MONTH;

        // 직전 같은 길이 기간. 서비스 이용 시작 전이면 비교하지 않는다
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = start.minusDays(days);
        boolean hasPrevious = !previousEnd.isBefore(serviceStart);

        // 기간 경계의 취소 시간과 겹치는 예약을 놓치지 않도록 앞뒤 하루씩 넓혀 읽고, 기간 지표는 inPeriod 로 거른다
        LocalDate loadFrom = hasPrevious ? previousStart : start;
        List<ReservationDashboardRow> rows = reservationDashboardRepository.findRows(
                shopId, loadFrom.minusDays(1).atStartOfDay(), end.plusDays(2).atStartOfDay());
        LocalDateTime visitCutoff = earlier(end.plusDays(1).atStartOfDay(), now);
        List<ReservationDashboardRepository.Visit> visits =
                reservationDashboardRepository.findVisits(shopId, visitCutoff);

        List<ReservationDashboardRow> period = inPeriod(rows, start, end);
        return new ReservationStatsResponse(
                granularity,
                serviceStart,
                summarize(rows, start, end, now, visits),
                hasPrevious ? summarize(rows, previousStart, previousEnd, now, visits) : null,
                trend(period, start, end, now, granularity),
                byWeekday(period),
                heatmap(period),
                byTreatment(period));
    }

    // 매장 가입일과 첫 예약일 중 이른 날. 매장 정보가 없으면 오늘
    private LocalDate serviceStartDate(Long shopId, LocalDate today) {
        return reservationDashboardRepository.findServiceStart(shopId)
                .map(row -> {
                    LocalDateTime shopCreatedAt = row.getShopCreatedAt();
                    LocalDateTime firstStartsAt = row.getFirstStartsAt();
                    if (shopCreatedAt == null) {
                        return firstStartsAt == null ? today : firstStartsAt.toLocalDate();
                    }
                    return firstStartsAt == null ? shopCreatedAt.toLocalDate()
                            : earlier(shopCreatedAt, firstStartsAt).toLocalDate();
                })
                .orElse(today);
    }

    private static List<ReservationDashboardRow> inPeriod(List<ReservationDashboardRow> rows, LocalDate start,
            LocalDate end) {
        return rows.stream()
                .filter(row -> {
                    LocalDate date = row.startsAt().toLocalDate();
                    return !date.isBefore(start) && !date.isAfter(end);
                })
                .toList();
    }

    private static long completedAmount(List<ReservationDashboardRow> rows, LocalDate start, LocalDate end,
            LocalDateTime now) {
        return inPeriod(rows, start, end).stream()
                .filter(row -> row.completed(now))
                .mapToLong(ReservationDashboardRow::appliedPrice)
                .sum();
    }

    private static ReservationPeriodSummary summarize(List<ReservationDashboardRow> rows, LocalDate start,
            LocalDate end, LocalDateTime now, List<ReservationDashboardRepository.Visit> visits) {
        List<ReservationDashboardRow> period = inPeriod(rows, start, end);
        int completed = 0;
        long completedAmount = 0;
        int upcoming = 0;
        long upcomingAmount = 0;
        int customerCancel = 0;
        int shopFaultCancel = 0;
        long nonRefundedDeposit = 0;
        int lastMinute = 0;
        long lastMinuteAmount = 0;
        int noShow = 0;
        Set<Long> customers = new HashSet<>();
        for (ReservationDashboardRow row : period) {
            if (row.confirmed()) {
                if (row.noShowed()) {
                    noShow++;
                } else if (row.completed(now)) {
                    completed++;
                    completedAmount += row.appliedPrice();
                    customers.add(row.userId());
                } else {
                    upcoming++;
                    upcomingAmount += row.appliedPrice();
                }
                if (isLastMinute(row)) {
                    lastMinute++;
                    lastMinuteAmount += row.appliedPrice();
                }
            } else if (row.cancelled()) {
                if (row.cancelType() == CancelType.SHOP_FAULT) {
                    shopFaultCancel++;
                } else {
                    customerCancel++;
                }
                nonRefundedDeposit += row.retainedDeposit();
            }
        }

        // 신규·재방문은 기간 끝 시점(아직 안 지났으면 지금)까지의 이 매장 전체 이용 횟수로 나눈다
        LocalDateTime cutoff = earlier(end.plusDays(1).atStartOfDay(), now);
        Map<Long, Integer> visitCounts = new HashMap<>();
        for (ReservationDashboardRepository.Visit visit : visits) {
            if (!visit.getEndsAt().isAfter(cutoff) && customers.contains(visit.getUserId())) {
                visitCounts.merge(visit.getUserId(), 1, Integer::sum);
            }
        }
        int returning = (int) customers.stream()
                .filter(userId -> CustomerSegment.of(visitCounts.getOrDefault(userId, 1))
                        == CustomerSegment.RETURNING)
                .count();

        Rebook rebook = rebook(rows, period);
        int confirmed = completed + upcoming + noShow;
        return new ReservationPeriodSummary(
                start,
                end,
                completed,
                completedAmount,
                upcoming,
                upcomingAmount,
                completed == 0 ? null : Math.round((double) completedAmount / completed),
                customerCancel,
                shopFaultCancel,
                percent(customerCancel, confirmed + customerCancel + shopFaultCancel),
                nonRefundedDeposit,
                rebook.cancelledMinutes(),
                rebook.rebookedMinutes(),
                percent(rebook.rebookedMinutes(), rebook.cancelledMinutes()),
                rebook.amount(),
                lastMinute,
                lastMinuteAmount,
                customers.size(),
                customers.size() - returning,
                returning,
                percent(returning, customers.size()),
                noShow,
                percent(noShow, completed + noShow));
    }

    /**
     * 취소로 비었던 시간과 그중 다시 채운 시간.
     * 비었던 시간: 기간 안 취소 완료 예약의 시간 합집합. 매장이 취소하며 닫은 시간은 다시 팔 수 없으므로 뺀다.
     * 다시 채운 시간: 그 취소가 완료된 뒤 생성된 확정 예약(노쇼 제외)과 겹치는 부분의 합집합.
     * 금액: 겹치는 부분이 있는 확정 예약의 적용가 합계 (한 예약은 한 번만 센다).
     * 예약은 날짜를 넘기지 않으므로 같은 날짜의 예약끼리만 비교한다.
     */
    private static Rebook rebook(List<ReservationDashboardRow> rows, List<ReservationDashboardRow> period) {
        List<ReservationDashboardRow> cancels = period.stream()
                .filter(row -> row.cancelled() && row.cancelCompletedAt() != null && !row.closedAfterCancel())
                .toList();
        if (cancels.isEmpty()) {
            return new Rebook(0, 0, 0);
        }
        Map<LocalDate, List<ReservationDashboardRow>> confirmedByDate = new HashMap<>();
        for (ReservationDashboardRow row : rows) {
            // 노쇼 예약은 자리를 채웠다고 보지 않는다
            if (row.occupied()) {
                confirmedByDate.computeIfAbsent(row.startsAt().toLocalDate(), date -> new ArrayList<>()).add(row);
            }
        }
        List<Interval> cancelled = new ArrayList<>();
        List<Interval> refilled = new ArrayList<>();
        Set<Long> rebookedIds = new HashSet<>();
        long amount = 0;
        for (ReservationDashboardRow cancel : cancels) {
            cancelled.add(new Interval(cancel.startsAt(), cancel.endsAt()));
            for (ReservationDashboardRow booking : confirmedByDate.getOrDefault(
                    cancel.startsAt().toLocalDate(), List.of())) {
                if (booking.createdAt().isBefore(cancel.cancelCompletedAt())) {
                    continue;
                }
                LocalDateTime overlapStart = later(booking.startsAt(), cancel.startsAt());
                LocalDateTime overlapEnd = earlier(booking.endsAt(), cancel.endsAt());
                if (overlapStart.isBefore(overlapEnd)) {
                    refilled.add(new Interval(overlapStart, overlapEnd));
                    if (rebookedIds.add(booking.reservationId())) {
                        amount += booking.appliedPrice();
                    }
                }
            }
        }
        return new Rebook(amount, unionMinutes(cancelled), unionMinutes(refilled));
    }

    private static long unionMinutes(List<Interval> intervals) {
        List<Interval> sorted = intervals.stream().sorted(Comparator.comparing(Interval::start)).toList();
        long minutes = 0;
        LocalDateTime currentStart = null;
        LocalDateTime currentEnd = null;
        for (Interval interval : sorted) {
            if (currentEnd != null && !interval.start().isAfter(currentEnd)) {
                currentEnd = later(currentEnd, interval.end());
                continue;
            }
            if (currentStart != null) {
                minutes += Duration.between(currentStart, currentEnd).toMinutes();
            }
            currentStart = interval.start();
            currentEnd = interval.end();
        }
        if (currentStart != null) {
            minutes += Duration.between(currentStart, currentEnd).toMinutes();
        }
        return minutes;
    }

    // 기간을 날짜 또는 달 단위 칸으로 나눈다. 달 단위의 첫·마지막 칸은 조회 기간에 걸친 날짜만 담는다
    private static List<ReservationStatsResponse.Trend> trend(List<ReservationDashboardRow> period, LocalDate start,
            LocalDate end, LocalDateTime now, Granularity granularity) {
        Map<LocalDate, TrendAccumulator> buckets = new LinkedHashMap<>();
        for (LocalDate bucketStart = start; !bucketStart.isAfter(end); ) {
            LocalDate bucketEnd = granularity == Granularity.DAY ? bucketStart
                    : earlier(bucketStart.withDayOfMonth(bucketStart.lengthOfMonth()), end);
            buckets.put(bucketStart, new TrendAccumulator(bucketEnd));
            bucketStart = bucketEnd.plusDays(1);
        }
        for (ReservationDashboardRow row : period) {
            LocalDate date = row.startsAt().toLocalDate();
            LocalDate key = granularity == Granularity.DAY ? date : later(date.withDayOfMonth(1), start);
            TrendAccumulator acc = buckets.get(key);
            if (acc == null) {
                continue;
            }
            if (row.completed(now)) {
                acc.completed++;
                acc.completedAmount += row.appliedPrice();
            } else if (row.upcoming(now)) {
                acc.upcoming++;
                acc.upcomingAmount += row.appliedPrice();
            } else if (row.cancelled()) {
                acc.cancelled++;
            }
        }
        List<ReservationStatsResponse.Trend> result = new ArrayList<>();
        buckets.forEach((bucketStart, acc) -> result.add(new ReservationStatsResponse.Trend(bucketStart, acc.end,
                acc.completed, acc.completedAmount, acc.upcoming, acc.upcomingAmount, acc.cancelled)));
        return result;
    }

    private static List<ReservationStatsResponse.Weekday> byWeekday(List<ReservationDashboardRow> period) {
        Map<DayOfWeek, int[]> counts = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, long[]> amounts = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            counts.put(day, new int[1]);
            amounts.put(day, new long[1]);
        }
        for (ReservationDashboardRow row : period) {
            if (row.occupied()) {
                DayOfWeek day = row.startsAt().getDayOfWeek();
                counts.get(day)[0]++;
                amounts.get(day)[0] += row.appliedPrice();
            }
        }
        List<ReservationStatsResponse.Weekday> result = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            result.add(new ReservationStatsResponse.Weekday(day, counts.get(day)[0], amounts.get(day)[0]));
        }
        return result;
    }

    // 예약 시간을 정시 경계로 잘라 요일·시간대 칸에 더한다
    private static List<ReservationStatsResponse.HeatmapCell> heatmap(List<ReservationDashboardRow> period) {
        Map<DayOfWeek, TreeMap<Integer, Long>> minutes = new EnumMap<>(DayOfWeek.class);
        for (ReservationDashboardRow row : period) {
            if (!row.occupied()) {
                continue;
            }
            LocalDateTime cursor = row.startsAt();
            while (cursor.isBefore(row.endsAt())) {
                LocalDateTime nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1);
                LocalDateTime sliceEnd = earlier(nextHour, row.endsAt());
                long sliceMinutes = Duration.between(cursor, sliceEnd).toMinutes();
                minutes.computeIfAbsent(cursor.getDayOfWeek(), day -> new TreeMap<>())
                        .merge(cursor.getHour(), sliceMinutes, Long::sum);
                cursor = sliceEnd;
            }
        }
        List<ReservationStatsResponse.HeatmapCell> result = new ArrayList<>();
        minutes.forEach((day, byHour) -> byHour.forEach((hour, value) ->
                result.add(new ReservationStatsResponse.HeatmapCell(day, hour, value))));
        return result;
    }

    private static List<ReservationStatsResponse.Treatment> byTreatment(List<ReservationDashboardRow> period) {
        Map<Long, TreatmentAccumulator> byId = new LinkedHashMap<>();
        long total = 0;
        for (ReservationDashboardRow row : period) {
            if (!row.occupied()) {
                continue;
            }
            TreatmentAccumulator acc = byId.computeIfAbsent(row.treatmentId(),
                    id -> new TreatmentAccumulator(row.treatmentName()));
            acc.count++;
            acc.amount += row.appliedPrice();
            total += row.appliedPrice();
        }
        long totalAmount = total;
        return byId.entrySet().stream()
                .map(entry -> new ReservationStatsResponse.Treatment(entry.getKey(), entry.getValue().name,
                        entry.getValue().count, entry.getValue().amount, percent(entry.getValue().amount, totalAmount)))
                .sorted(Comparator.comparingLong(ReservationStatsResponse.Treatment::amount).reversed()
                        .thenComparing(Comparator.comparingInt(ReservationStatsResponse.Treatment::count).reversed())
                        .thenComparing(ReservationStatsResponse.Treatment::treatmentId))
                .toList();
    }

    // 30분 간격으로 이어지는 시작 시각을 하나의 시간대로 묶는다. 끝은 마지막 시각 + 30분
    private static List<ReservationDashboardResponse.AvailableRange> toRanges(List<LocalDateTime> startTimes) {
        long unit = BookingPolicy.TIME_UNIT_MINUTES;
        List<ReservationDashboardResponse.AvailableRange> ranges = new ArrayList<>();
        LocalDateTime rangeStart = null;
        LocalDateTime rangeEnd = null;
        for (LocalDateTime startTime : startTimes) {
            if (rangeEnd != null && rangeEnd.equals(startTime)) {
                rangeEnd = startTime.plusMinutes(unit);
                continue;
            }
            if (rangeStart != null) {
                ranges.add(new ReservationDashboardResponse.AvailableRange(
                        TimeUtils.toOffset(rangeStart), TimeUtils.toOffset(rangeEnd)));
            }
            rangeStart = startTime;
            rangeEnd = startTime.plusMinutes(unit);
        }
        if (rangeStart != null) {
            ranges.add(new ReservationDashboardResponse.AvailableRange(
                    TimeUtils.toOffset(rangeStart), TimeUtils.toOffset(rangeEnd)));
        }
        return ranges;
    }

    // 예약 테이블에 가격 구간 컬럼이 없어 점유 시작 시각(점유 만료 - 점유 시간)으로 구간을 다시 판단한다
    private static boolean isLastMinute(ReservationDashboardRow row) {
        LocalDateTime heldAt = row.holdExpiresAt().minus(BookingPolicy.HOLD_DURATION);
        return PriceTier.of(row.startsAt(), heldAt) == PriceTier.LAST_MINUTE;
    }

    private static Double percent(long part, long total) {
        if (total == 0) {
            return null;
        }
        return Math.round(part * 1000.0 / total) / 10.0;
    }

    private static Double changeRate(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return Math.round((current - previous) * 1000.0 / previous) / 10.0;
    }

    private static <T extends Comparable<? super T>> T later(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static <T extends Comparable<? super T>> T earlier(T a, T b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private record Interval(LocalDateTime start, LocalDateTime end) {
    }

    private record Rebook(long amount, long cancelledMinutes, long rebookedMinutes) {
    }

    private static final class TrendAccumulator {

        private final LocalDate end;
        private int completed;
        private long completedAmount;
        private int upcoming;
        private long upcomingAmount;
        private int cancelled;

        private TrendAccumulator(LocalDate end) {
            this.end = end;
        }
    }

    private static final class TreatmentAccumulator {

        private final String name;
        private int count;
        private long amount;

        private TreatmentAccumulator(String name) {
            this.name = name;
        }
    }
}
