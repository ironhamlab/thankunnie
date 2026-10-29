package com.ucamp.app.domain.reservation.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.ucamp.app.domain.reservation.dto.response.ReservationStatsResponse;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationDashboardCounts;
import com.ucamp.app.domain.reservation.repository.ReservationDashboardRepository;
import com.ucamp.app.domain.reservation.repository.ReservationRebookStats;
import com.ucamp.app.domain.reservation.repository.ReservationStatsRow;
import com.ucamp.app.domain.vacancy.policy.PriceTier;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

/**
 * 사장님 메인 대시보드. 매장은 로그인한 사장님의 매장으로 정해진다.
 */
@Service
@Transactional(readOnly = true)
public class ReservationDashboardService {

    /** 통계 기본 조회 기간: 오늘 포함 30일 */
    private static final int DEFAULT_STATS_DAYS = 30;
    /** 통계 최대 조회 기간: 92일 (약 3개월) */
    private static final int MAX_STATS_DAYS = 92;

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
        LocalDate monthFrom = today.withDayOfMonth(1);
        LocalDate monthTo = today.withDayOfMonth(today.lengthOfMonth());
        LocalDateTime periodStart = monthFrom.atStartOfDay();
        LocalDateTime periodEnd = monthTo.plusDays(1).atStartOfDay();

        ReservationDashboardCounts counts = reservationDashboardRepository.countReservations(
                shopId, periodStart, periodEnd, today.atStartOfDay(), now);
        Map<CancelType, Integer> cancelled =
                reservationDashboardRepository.countCancelledByType(shopId, periodStart, periodEnd);
        ReservationRebookStats rebooked = reservationDashboardRepository.countRebooked(shopId, periodStart, periodEnd);
        // 최소 시간 단위(30분)로 계산하면 "지금 예약할 수 있는 빈 시간"이 된다. 시술별 소요 시간은 고려하지 않는다
        List<LocalDateTime> availableStartTimes =
                availabilityService.getAvailableStartTimes(shopId, today, BookingPolicy.TIME_UNIT_MINUTES);

        int customer = cancelled.getOrDefault(CancelType.CUSTOMER, 0);
        int shopFault = cancelled.getOrDefault(CancelType.SHOP_FAULT, 0);
        int customerRequest = cancelled.getOrDefault(CancelType.CUSTOMER_REQUEST, 0);
        return new ReservationDashboardResponse(
                today,
                new ReservationDashboardResponse.Today(
                        counts.todayConfirmedCount(),
                        counts.todayUpcomingCount(),
                        availableStartTimes.stream().map(TimeUtils::toOffset).toList(),
                        toRanges(availableStartTimes)),
                new ReservationDashboardResponse.Month(
                        monthFrom,
                        monthTo,
                        counts.confirmedCount(),
                        counts.expectedSales(),
                        customer + shopFault + customerRequest,
                        customer,
                        shopFault,
                        customerRequest,
                        counts.cancelPendingCount(),
                        rebooked.count(),
                        rebooked.sales()));
    }

    /**
     * 기간 통계. from·to 는 시술일 기준이며 둘 다 포함한다. 생략하면 to 는 오늘, from 은 to 포함 30일 전이다.
     */
    public ReservationStatsResponse getOwnerStats(AuthUser loginUser, LocalDate from, LocalDate to) {
        loginUser.requireRole(UserRole.OWNER);
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_STATS_DAYS - 1L) : from;
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "to 는 from 과 같거나 이후 날짜여야 합니다.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_STATS_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간은 최대 " + MAX_STATS_DAYS + "일입니다.");
        }

        List<ReservationStatsRow> rows = reservationDashboardRepository.findStatsRows(
                loginUser.shopId(), start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        return new ReservationStatsResponse(start, end, summarize(rows), daily(rows, start, end),
                byTreatment(rows), byHour(rows));
    }

    private static ReservationStatsResponse.Summary summarize(List<ReservationStatsRow> rows) {
        int confirmed = 0;
        long sales = 0;
        int cancelled = 0;
        int rebooked = 0;
        long rebookedSales = 0;
        int lastMinute = 0;
        long lastMinuteSales = 0;
        for (ReservationStatsRow row : rows) {
            if (row.status() == ReservationStatus.CANCELLED) {
                cancelled++;
                continue;
            }
            confirmed++;
            sales += row.appliedPrice();
            if (row.rebooked()) {
                rebooked++;
                rebookedSales += row.appliedPrice();
            }
            if (isLastMinute(row)) {
                lastMinute++;
                lastMinuteSales += row.appliedPrice();
            }
        }
        return new ReservationStatsResponse.Summary(confirmed, sales, cancelled, rebooked, rebookedSales,
                lastMinute, lastMinuteSales);
    }

    private static List<ReservationStatsResponse.Daily> daily(List<ReservationStatsRow> rows, LocalDate start,
            LocalDate end) {
        Map<LocalDate, int[]> counts = new LinkedHashMap<>();
        Map<LocalDate, Long> sales = new LinkedHashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            counts.put(date, new int[2]);
            sales.put(date, 0L);
        }
        for (ReservationStatsRow row : rows) {
            LocalDate date = row.startsAt().toLocalDate();
            if (row.status() == ReservationStatus.CANCELLED) {
                counts.get(date)[1]++;
            } else {
                counts.get(date)[0]++;
                sales.merge(date, (long) row.appliedPrice(), Long::sum);
            }
        }
        List<ReservationStatsResponse.Daily> result = new ArrayList<>();
        counts.forEach((date, count) ->
                result.add(new ReservationStatsResponse.Daily(date, count[0], sales.get(date), count[1])));
        return result;
    }

    private static List<ReservationStatsResponse.Treatment> byTreatment(List<ReservationStatsRow> rows) {
        Map<Long, TreatmentAccumulator> byId = new LinkedHashMap<>();
        for (ReservationStatsRow row : rows) {
            TreatmentAccumulator acc = byId.computeIfAbsent(row.treatmentId(),
                    id -> new TreatmentAccumulator(id, row.treatmentName()));
            if (row.status() == ReservationStatus.CANCELLED) {
                acc.cancelled++;
            } else {
                acc.confirmed++;
                acc.sales += row.appliedPrice();
            }
        }
        return byId.values().stream()
                .sorted(Comparator.comparingLong((TreatmentAccumulator acc) -> acc.sales).reversed()
                        .thenComparing(Comparator.comparingInt((TreatmentAccumulator acc) -> acc.confirmed)
                                .reversed())
                        .thenComparingLong(acc -> acc.treatmentId))
                .map(acc -> new ReservationStatsResponse.Treatment(acc.treatmentId, acc.treatmentName,
                        acc.confirmed, acc.sales, acc.cancelled))
                .toList();
    }

    private static List<ReservationStatsResponse.Hourly> byHour(List<ReservationStatsRow> rows) {
        // 요일(월=1)·시 순으로 정렬되도록 요일*100+시를 키로 쓴다
        Map<Integer, Integer> counts = new TreeMap<>();
        for (ReservationStatsRow row : rows) {
            if (row.status() == ReservationStatus.CONFIRMED) {
                int key = row.startsAt().getDayOfWeek().getValue() * 100 + row.startsAt().getHour();
                counts.merge(key, 1, Integer::sum);
            }
        }
        List<ReservationStatsResponse.Hourly> result = new ArrayList<>();
        counts.forEach((key, count) ->
                result.add(new ReservationStatsResponse.Hourly(DayOfWeek.of(key / 100), key % 100, count)));
        return result;
    }

    // 30분 간격으로 이어지는 시작 시각을 하나의 구간으로 묶는다
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
    private static boolean isLastMinute(ReservationStatsRow row) {
        LocalDateTime heldAt = row.holdExpiresAt().minus(BookingPolicy.HOLD_DURATION);
        return PriceTier.of(row.startsAt(), heldAt) == PriceTier.LAST_MINUTE;
    }

    private static final class TreatmentAccumulator {

        private final Long treatmentId;
        private final String treatmentName;
        private int confirmed;
        private long sales;
        private int cancelled;

        private TreatmentAccumulator(Long treatmentId, String treatmentName) {
            this.treatmentId = treatmentId;
            this.treatmentName = treatmentName;
        }
    }
}
