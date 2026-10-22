package com.ucamp.app.domain.vacancy.policy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.policy.ReservationStatus;

/**
 * 빈자리 판매 가능 조건 (B-06). 시간·상태 공통 검사로 A·C·D·E 가 재사용한다.
 * 시간 조건은 저장된 상태와 관계없이 우선 적용하며, 마감 시각과 같은 시점은 마감된 것으로 본다.
 */
public final class SalePolicy {

    private SalePolicy() {
    }

    /**
     * 예약 클릭 가능 여부. 검색 목록 필터에 사용한다.
     *
     * @param holdExpiresAt 임시확보만료일시, 점유 중이 아니면 null
     */
    public static boolean isSellable(ReservationStatus status, LocalDateTime holdExpiresAt,
            LocalDateTime startsAt, LocalDateTime now) {
        return isBookingTime(startsAt, now) && isAvailableStatus(status, holdExpiresAt, now);
    }

    /**
     * 예약 클릭(임시 확보) 시점에 호출한다. 시간 마감 → 점유 중 → 판매 중 아님 순서로 검사한다.
     */
    public static void validateSellable(ReservationStatus status, LocalDateTime holdExpiresAt,
            LocalDateTime startsAt, LocalDateTime now) {
        if (!isBookingTime(startsAt, now)) {
            throw new BusinessException(ErrorCode.VACANCY_BOOKING_CLOSED);
        }
        if (isHoldActive(status, holdExpiresAt, now)) {
            throw new BusinessException(ErrorCode.VACANCY_HELD);
        }
        if (!isAvailableStatus(status, holdExpiresAt, now)) {
            throw new BusinessException(ErrorCode.VACANCY_NOT_ON_SALE);
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
     * 사장님 수정·중지 가능 여부를 검사한다. 점유 중이거나 예약 완료·취소 처리 중이면 차단한다.
     */
    public static void validateEditable(ReservationStatus status, LocalDateTime holdExpiresAt, LocalDateTime now) {
        boolean editable = status == ReservationStatus.CLOSED || isAvailableStatus(status, holdExpiresAt, now);
        if (!editable) {
            throw new BusinessException(ErrorCode.VACANCY_NOT_EDITABLE);
        }
    }

    /**
     * 점유 만료 시각. 예약 클릭 시점 + 5분.
     */
    public static LocalDateTime holdExpiresAt(LocalDateTime now) {
        return now.plus(BookingPolicy.HOLD_DURATION);
    }

    /**
     * 빈자리 등록·수정 시 시작 시각을 검사한다. 30분 단위, 시작 30분 전까지, 오늘 포함 60일 이내.
     */
    public static void validateRegistrable(LocalDateTime startsAt, LocalDateTime now) {
        boolean onUnit = startsAt.getMinute() % BookingPolicy.START_TIME_UNIT_MINUTES == 0
                && startsAt.getSecond() == 0
                && startsAt.getNano() == 0;
        if (!onUnit) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_START_TIME);
        }
        if (!now.isBefore(startsAt.minus(BookingPolicy.REGISTRATION_DEADLINE))) {
            throw new BusinessException(ErrorCode.VACANCY_REGISTRATION_CLOSED);
        }
        LocalDate lastDate = now.toLocalDate().plusDays(BookingPolicy.REGISTRATION_MAX_DAYS - 1L);
        if (startsAt.toLocalDate().isAfter(lastDate)) {
            throw new BusinessException(ErrorCode.VACANCY_OUT_OF_RANGE);
        }
    }

    // 시작 20분 전(결제 마감 15분 + 점유 5분)까지 클릭 가능
    private static boolean isBookingTime(LocalDateTime startsAt, LocalDateTime now) {
        return now.isBefore(startsAt.minus(BookingPolicy.BOOKING_DEADLINE));
    }

    // 판매 중이거나, 점유했지만 만료 시각이 지난 경우
    private static boolean isAvailableStatus(ReservationStatus status, LocalDateTime holdExpiresAt,
            LocalDateTime now) {
        return status == ReservationStatus.OPEN
                || (status == ReservationStatus.HOLD && !isHoldActive(status, holdExpiresAt, now));
    }

    // 만료 시각이 없으면 점유 중으로 본다 (값이 없을 때 판매를 허용하지 않는다)
    private static boolean isHoldActive(ReservationStatus status, LocalDateTime holdExpiresAt, LocalDateTime now) {
        return status == ReservationStatus.HOLD && (holdExpiresAt == null || now.isBefore(holdExpiresAt));
    }
}
