package com.ucamp.app.domain.schedule.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.ShopLock;
import com.ucamp.app.common.policy.ScheduleExceptionType;
import com.ucamp.app.common.util.KoreaTime;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.schedule.dto.request.ScheduleExceptionSaveRequest;
import com.ucamp.app.domain.schedule.dto.response.ScheduleExceptionResponse;
import com.ucamp.app.domain.schedule.entity.ShopScheduleException;
import com.ucamp.app.domain.schedule.repository.ShopScheduleExceptionRepository;
import com.ucamp.app.domain.vacancy.policy.SalePolicy;

/**
 * 시간대 열기·닫기(예외 일정) 관리 (B-02·B-03·B-04). 사장님 본인 매장만 다룬다.
 * 예외 일정끼리 겹치는 것은 허용한다. 가용 시간 계산에서 닫기가 열기보다 우선한다.
 * 변경으로 시간이 닫히게 될 때 그 시간을 잡고 있는 예약이 있으면 차단하며, 고객 예약과 동시에 들어와도
 * 한쪽만 성공하도록 매장 잠금 후 검사한다.
 */
@Service
@Transactional(readOnly = true)
public class ShopScheduleExceptionService {

    private final ShopScheduleExceptionRepository shopScheduleExceptionRepository;
    private final ReservationRepository reservationRepository;
    private final ShopLock shopLock;
    private final Clock clock;

    public ShopScheduleExceptionService(ShopScheduleExceptionRepository shopScheduleExceptionRepository,
            ReservationRepository reservationRepository, ShopLock shopLock, Clock clock) {
        this.shopScheduleExceptionRepository = shopScheduleExceptionRepository;
        this.reservationRepository = reservationRepository;
        this.shopLock = shopLock;
        this.clock = clock;
    }

    @Transactional
    public ScheduleExceptionResponse createException(AuthUser loginUser, ScheduleExceptionSaveRequest request) {
        loginUser.requireRole(UserRole.OWNER);
        Long shopId = loginUser.shopId();
        LocalDateTime startsAt = KoreaTime.toLocal(request.startsAt());
        LocalDateTime endsAt = KoreaTime.toLocal(request.endsAt());
        LocalDateTime now = now();
        SalePolicy.validateExceptionTime(request.exceptionType(), startsAt, endsAt, now);

        shopLock.lock(shopId);
        if (request.exceptionType() == ScheduleExceptionType.CLOSE) {
            requireNoReservation(shopId, startsAt, endsAt, now, reservation -> true);
        }

        ShopScheduleException exception = ShopScheduleException.builder()
                .shopId(shopId)
                .exceptionType(request.exceptionType())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .reason(request.reason())
                .build();
        return ScheduleExceptionResponse.from(shopScheduleExceptionRepository.save(exception));
    }

    /**
     * 본인 매장의 [from 00:00, to 다음 날 00:00) 구간과 겹치는 일정을 시작 시각 순으로 조회한다.
     */
    public List<ScheduleExceptionResponse> getExceptions(AuthUser loginUser, LocalDate from, LocalDate to) {
        loginUser.requireRole(UserRole.OWNER);
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VACANCY_INVALID_TIME_RANGE);
        }
        return shopScheduleExceptionRepository
                .findOverlapping(loginUser.shopId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream()
                .map(ScheduleExceptionResponse::from)
                .toList();
    }

    @Transactional
    public ScheduleExceptionResponse updateException(AuthUser loginUser, Long exceptionId,
            ScheduleExceptionSaveRequest request) {
        ShopScheduleException exception = findOwnedException(loginUser, exceptionId);
        ScheduleExceptionType newType = request.exceptionType();
        LocalDateTime startsAt = KoreaTime.toLocal(request.startsAt());
        LocalDateTime endsAt = KoreaTime.toLocal(request.endsAt());
        LocalDateTime now = now();
        SalePolicy.validateExceptionTime(newType, startsAt, endsAt, now);

        shopLock.lock(exception.getShopId());
        if (newType == ScheduleExceptionType.CLOSE) {
            requireNoReservation(exception.getShopId(), startsAt, endsAt, now, reservation -> true);
        }
        if (exception.getExceptionType() == ScheduleExceptionType.OPEN) {
            // 기존 열기 구간의 예약 중 새 열기 구간에 다 들어가지 않는 예약은 열린 시간을 잃는다
            requireNoReservation(exception.getShopId(), exception.getStartsAt(), exception.getEndsAt(), now,
                    reservation -> newType != ScheduleExceptionType.OPEN
                            || !isWithin(reservation, startsAt, endsAt));
        }

        exception.change(newType, startsAt, endsAt, request.reason());
        return ScheduleExceptionResponse.from(exception);
    }

    @Transactional
    public void deleteException(AuthUser loginUser, Long exceptionId) {
        ShopScheduleException exception = findOwnedException(loginUser, exceptionId);
        shopLock.lock(exception.getShopId());
        if (exception.getExceptionType() == ScheduleExceptionType.OPEN) {
            requireNoReservation(exception.getShopId(), exception.getStartsAt(), exception.getEndsAt(), now(),
                    reservation -> true);
        }
        shopScheduleExceptionRepository.delete(exception);
    }

    private ShopScheduleException findOwnedException(AuthUser loginUser, Long exceptionId) {
        ShopScheduleException exception = shopScheduleExceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VACANCY_SCHEDULE_EXCEPTION_NOT_FOUND));
        loginUser.requireShopOwner(exception.getShopId());
        return exception;
    }

    /**
     * 구간과 겹치고 시간을 잡고 있는 예약 중 affected 에 해당하는 예약이 있으면 차단한다.
     */
    private void requireNoReservation(Long shopId, LocalDateTime startsAt, LocalDateTime endsAt,
            LocalDateTime now, Predicate<Reservation> affected) {
        boolean reserved = reservationRepository.findActiveOverlapping(shopId, startsAt, endsAt).stream()
                .filter(reservation -> SalePolicy.isOccupying(
                        reservation.getStatus(), reservation.getHoldExpiresAt(), now))
                .anyMatch(affected);
        if (reserved) {
            throw new BusinessException(ErrorCode.VACANCY_RESERVED_TIME);
        }
    }

    private static boolean isWithin(Reservation reservation, LocalDateTime startsAt, LocalDateTime endsAt) {
        return !reservation.getStartsAt().isBefore(startsAt) && !reservation.getEndsAt().isAfter(endsAt);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
