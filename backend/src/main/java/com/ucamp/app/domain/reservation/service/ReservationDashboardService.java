package com.ucamp.app.domain.reservation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.ucamp.app.domain.reservation.repository.ReservationDashboardRepository;
import com.ucamp.app.domain.reservation.repository.ReservationDashboardRow;
import com.ucamp.app.domain.vacancy.policy.PriceTier;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

/**
 * 사장님 메인 대시보드와 기간 통계. 매장은 로그인한 사장님의 매장으로 정해진다.
 * 대시보드 이번 달 요약과 통계 요약은 같은 계산(summarize)을 써서 숫자가 어긋나지 않게 한다.
 */
@Service
@Transactional(readOnly = true)
public class ReservationDashboardService {

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

        List<ReservationDashboardRow> rows = loadRows(shopId, monthFrom, monthTo);
        List<ReservationDashboardRow> todayConfirmed = rows.stream()
                .filter(row -> row.confirmed() && row.startsAt().toLocalDate().equals(today))
                .toList();
        int upcoming = (int) todayConfirmed.stream().filter(row -> row.startsAt().isAfter(now)).count();

        // 최소 시간 단위(30분)로 계산한 "지금 예약받을 수 있는 시간". 시술별 소요 시간은 고려하지 않는다
        List<LocalDateTime> availableStartTimes =
                availabilityService.getAvailableStartTimes(shopId, today, BookingPolicy.TIME_UNIT_MINUTES);
        boolean closed = !availabilityService.hasOpenTime(shopId, today);

        return new ReservationDashboardResponse(
                today,
                new ReservationDashboardResponse.Today(
                        todayConfirmed.size(),
                        upcoming,
                        closed,
                        (long) availableStartTimes.size() * BookingPolicy.TIME_UNIT_MINUTES,
                        toRanges(availableStartTimes)),
                summarize(rows, monthFrom, monthTo));
    }

    /**
     * 기간 통계. from·to 는 시술일 기준이며 둘 다 포함한다.
     * 둘 다 생략하면 이번 달 1일~말일, from 만 생략하면 to 가 속한 달 1일부터, to 만 생략하면 from 이 속한 달 말일까지다.
     */
    public ReservationStatsResponse getOwnerStats(AuthUser loginUser, LocalDate from, LocalDate to) {
        loginUser.requireRole(UserRole.OWNER);
        LocalDate today = LocalDate.now(clock);
        LocalDate start;
        LocalDate end;
        if (from == null && to == null) {
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
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_STATS_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간은 최대 " + MAX_STATS_DAYS + "일입니다.");
        }

        List<ReservationDashboardRow> rows = loadRows(loginUser.shopId(), start, end);
        return new ReservationStatsResponse(summarize(rows, start, end), daily(rows, start, end),
                byTreatment(inPeriod(rows, start, end)));
    }

    // 기간 경계의 취소 시간과 겹치는 예약을 놓치지 않도록 앞뒤 하루씩 넓혀 읽고, 기간 지표는 inPeriod 로 거른다
    private List<ReservationDashboardRow> loadRows(Long shopId, LocalDate start, LocalDate end) {
        return reservationDashboardRepository.findRows(
                shopId, start.minusDays(1).atStartOfDay(), end.plusDays(2).atStartOfDay());
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

    private static ReservationPeriodSummary summarize(List<ReservationDashboardRow> rows, LocalDate start, LocalDate end) {
        List<ReservationDashboardRow> period = inPeriod(rows, start, end);
        int confirmed = 0;
        long bookedAmount = 0;
        int customerCancel = 0;
        int shopFaultCancel = 0;
        long retainedDeposit = 0;
        int lastMinute = 0;
        long lastMinuteAmount = 0;
        for (ReservationDashboardRow row : period) {
            if (row.confirmed()) {
                confirmed++;
                bookedAmount += row.appliedPrice();
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
                retainedDeposit += row.retainedDeposit();
            }
        }
        Rebook rebook = rebook(rows, period);
        return new ReservationPeriodSummary(
                start,
                end,
                confirmed,
                bookedAmount,
                customerCancel,
                shopFaultCancel,
                percent(customerCancel, confirmed + customerCancel + shopFaultCancel),
                retainedDeposit,
                rebook.amount(),
                rebook.cancelledMinutes(),
                rebook.rebookedMinutes(),
                percent(rebook.rebookedMinutes(), rebook.cancelledMinutes()),
                lastMinute,
                lastMinuteAmount);
    }

    /**
     * 취소로 비었던 시간과 그중 다시 채운 시간.
     * 비었던 시간: 기간 안 취소 완료 예약의 시간 합집합. 사장님이 취소하며 닫은 시간은 다시 팔 수 없으므로 뺀다.
     * 다시 채운 시간: 그 취소가 완료된 뒤 생성된 확정 예약과 겹치는 부분의 합집합.
     * 금액: 겹치는 부분이 있는 확정 예약의 적용가 합계 (한 예약은 한 번만 센다).
     */
    private static Rebook rebook(List<ReservationDashboardRow> rows, List<ReservationDashboardRow> period) {
        List<ReservationDashboardRow> cancels = period.stream()
                .filter(row -> row.cancelled() && row.cancelCompletedAt() != null && !row.closedAfterCancel())
                .toList();
        List<Interval> cancelled = cancels.stream()
                .map(row -> new Interval(row.startsAt(), row.endsAt()))
                .toList();
        List<Interval> refilled = new ArrayList<>();
        Set<Long> rebookedIds = new HashSet<>();
        long amount = 0;
        for (ReservationDashboardRow booking : rows) {
            if (!booking.confirmed()) {
                continue;
            }
            for (ReservationDashboardRow cancel : cancels) {
                if (booking.createdAt().isBefore(cancel.cancelCompletedAt())) {
                    continue;
                }
                LocalDateTime overlapStart = max(booking.startsAt(), cancel.startsAt());
                LocalDateTime overlapEnd = min(booking.endsAt(), cancel.endsAt());
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
                currentEnd = max(currentEnd, interval.end());
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

    private static List<ReservationStatsResponse.Daily> daily(List<ReservationDashboardRow> rows, LocalDate start,
            LocalDate end) {
        Map<LocalDate, DailyAccumulator> byDate = new LinkedHashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            byDate.put(date, new DailyAccumulator());
        }
        for (ReservationDashboardRow row : rows) {
            DailyAccumulator acc = byDate.get(row.startsAt().toLocalDate());
            if (acc == null) {
                continue;
            }
            if (row.confirmed()) {
                acc.confirmed++;
                acc.amount += row.appliedPrice();
            } else if (row.cancelled()) {
                acc.cancelled++;
            }
        }
        List<ReservationStatsResponse.Daily> result = new ArrayList<>();
        byDate.forEach((date, acc) ->
                result.add(new ReservationStatsResponse.Daily(date, acc.confirmed, acc.amount, acc.cancelled)));
        return result;
    }

    private static List<ReservationStatsResponse.Treatment> byTreatment(List<ReservationDashboardRow> period) {
        Map<Long, ReservationStatsResponse.Treatment> byId = new LinkedHashMap<>();
        for (ReservationDashboardRow row : period) {
            if (!row.confirmed()) {
                continue;
            }
            byId.merge(row.treatmentId(),
                    new ReservationStatsResponse.Treatment(row.treatmentId(), row.treatmentName(), 1,
                            row.appliedPrice()),
                    (a, b) -> new ReservationStatsResponse.Treatment(a.treatmentId(), a.treatmentName(),
                            a.confirmedCount() + b.confirmedCount(), a.bookedAmount() + b.bookedAmount()));
        }
        return byId.values().stream()
                .sorted(Comparator.comparingLong(ReservationStatsResponse.Treatment::bookedAmount).reversed()
                        .thenComparing(Comparator.comparingInt(ReservationStatsResponse.Treatment::confirmedCount)
                                .reversed())
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

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private record Interval(LocalDateTime start, LocalDateTime end) {
    }

    private record Rebook(long amount, long cancelledMinutes, long rebookedMinutes) {
    }

    private static final class DailyAccumulator {

        private int confirmed;
        private long amount;
        private int cancelled;
    }
}
