package com.ucamp.app.domain.vacancy.policy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.policy.ReservationStatus;

/**
 * 예약 가능 시간 판단 공통 규칙 (B-06). A·C·D·E 가 재사용한다.
 * 시간 구간의 종료 시각은 포함하지 않으며, 마감 시각과 같은 시점은 마감된 것으로 본다.
 */
public final class SalePolicy {

    private SalePolicy() {
    }

    /**
     * 기존 예약이 시간을 잡고 있는지 판단한다. 빈 시간 계산(C)과 예약 겹침 검사(D)에 사용한다.
     * 예약 완료·취소 처리 중은 잡고 있고, 점유는 만료 시각 전까지만 잡고 있다.
     *
     * @param holdExpiresAt 임시확보만료일시
     */
    public static boolean isOccupying(ReservationStatus status, LocalDateTime holdExpiresAt, LocalDateTime now) {
        return switch (status) {
            case CONFIRMED, CANCEL_PENDING -> true;
            // 만료 시각이 없으면 잡고 있는 것으로 본다 (값이 없을 때 중복 예약을 허용하지 않는다)
            case HOLD -> holdExpiresAt == null || now.isBefore(holdExpiresAt);
            case CANCELLED, EXPIRED -> false;
        };
    }

    /**
     * 두 시간 구간이 겹치는지 판단한다. 종료 시각은 포함하지 않으므로 12:00~13:00 과 13:00~14:00 은 겹치지 않는다.
     */
    public static boolean overlaps(LocalDateTime startsAt, LocalDateTime endsAt,
            LocalDateTime otherStartsAt, LocalDateTime otherEndsAt) {
        return startsAt.isBefore(otherEndsAt) && otherStartsAt.isBefore(endsAt);
    }

    /**
     * 예약 클릭 가능 여부. 검색 목록 필터에 사용한다.
     */
    public static boolean isBookable(LocalDateTime startsAt, LocalDateTime now) {
        return isOnTimeUnit(startsAt) && isBeforeBookingDeadline(startsAt, now) && isWithinBookingDays(startsAt, now);
    }

    /**
     * 예약 클릭(점유) 시점에 호출한다. 30분 단위, 시작 20분 전까지, 오늘 포함 60일 이내.
     * 겹치는 예약 여부는 DB 조회가 필요하므로 호출하는 쪽에서 isOccupying·overlaps 로 검사한다.
     */
    public static void validateBookable(LocalDateTime startsAt, LocalDateTime now) {
        if (!isOnTimeUnit(startsAt)) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_TIME_UNIT);
        }
        if (!isBeforeBookingDeadline(startsAt, now)) {
            throw new BusinessException(ErrorCode.VACANCY_BOOKING_CLOSED);
        }
        if (!isWithinBookingDays(startsAt, now)) {
            throw new BusinessException(ErrorCode.VACANCY_OUT_OF_RANGE);
        }
    }

    /**
     * 결제 확정 시점에 호출한다. 시작 15분 전까지 가능하다.
     */
    public static void validatePayable(LocalDateTime startsAt, LocalDateTime now) {
        if (!now.isBefore(startsAt.minus(BookingPolicy.PAYMENT_DEADLINE))) {
            throw new BusinessException(ErrorCode.VACANCY_PAYMENT_CLOSED);
        }
    }

    /**
     * 점유 만료 시각. 예약 클릭 시점 + 5분.
     */
    public static LocalDateTime holdExpiresAt(LocalDateTime now) {
        return now.plus(BookingPolicy.HOLD_DURATION);
    }

    /**
     * 사장님 예외 일정(추가 열기·닫기)의 시각을 검사한다. 30분 단위, 종료가 시작보다 뒤.
     */
    public static void validateExceptionTime(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (!isOnTimeUnit(startsAt) || !isOnTimeUnit(endsAt)) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_TIME_UNIT);
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_TIME_RANGE);
        }
    }

    private static boolean isOnTimeUnit(LocalDateTime time) {
        return time.getMinute() % BookingPolicy.TIME_UNIT_MINUTES == 0
                && time.getSecond() == 0
                && time.getNano() == 0;
    }

    // 시작 20분 전(결제 마감 15분 + 점유 5분)까지 클릭 가능
    private static boolean isBeforeBookingDeadline(LocalDateTime startsAt, LocalDateTime now) {
        return now.isBefore(startsAt.minus(BookingPolicy.BOOKING_DEADLINE));
    }

    private static boolean isWithinBookingDays(LocalDateTime startsAt, LocalDateTime now) {
        LocalDate lastDate = now.toLocalDate().plusDays(BookingPolicy.BOOKING_MAX_DAYS - 1L);
        return !startsAt.toLocalDate().isAfter(lastDate);
    }
}
