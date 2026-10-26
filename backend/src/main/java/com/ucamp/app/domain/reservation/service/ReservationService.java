package com.ucamp.app.domain.reservation.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.ShopLock;
import com.ucamp.app.common.lock.UserLock;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.reservation.dto.request.ReservationCreateRequest;
import com.ucamp.app.domain.reservation.dto.response.ReservationCreateResponse;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;
import com.ucamp.app.domain.vacancy.policy.PriceCalculator;
import com.ucamp.app.domain.vacancy.policy.PriceQuote;
import com.ucamp.app.domain.vacancy.policy.SalePolicy;
import com.ucamp.app.domain.vacancy.policy.SalePrices;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TreatmentRepository treatmentRepository;
    private final AvailabilityService availabilityService;
    private final ShopLock shopLock;
    private final UserLock userLock;
    private final Clock clock;

    public ReservationService(ReservationRepository reservationRepository,
            TreatmentRepository treatmentRepository, AvailabilityService availabilityService,
            ShopLock shopLock, UserLock userLock, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.treatmentRepository = treatmentRepository;
        this.availabilityService = availabilityService;
        this.shopLock = shopLock;
        this.userLock = userLock;
        this.clock = clock;
    }

    /**
     * 예약 클릭 시 점유(HOLD) 예약을 만든다. 가격 구간과 예약금은 이 시점 기준으로 고정한다.
     */
    @Transactional
    public ReservationCreateResponse createReservation(AuthUser loginUser, ReservationCreateRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);

        LocalDateTime startsAt = TimeUtils.toLocal(request.startsAt());
        // 잠금을 기다리기 전에 마감·단위·기간이 틀린 요청은 바로 돌려보낸다
        SalePolicy.validateBookable(startsAt, LocalDateTime.now(clock));

        Treatment treatment = treatmentRepository.findById(request.treatmentId())
                .filter(found -> found.getStatus() == TreatmentStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.TREATMENT_NOT_FOUND));
        Long shopId = treatment.getShop().getShopId();
        LocalDateTime endsAt = startsAt.plusMinutes(treatment.getDurationMinutes());

        // 같은 고객이 여러 매장에 동시에 클릭해도 본인 겹침 검사를 함께 통과하지 않도록 회원 단위로 먼저 순서를 맞춘다
        userLock.lock(loginUser.userId());
        // 같은 시간에 두 고객이 동시에 클릭해도 한 명만 점유하도록 매장 단위로 순서를 맞춘다
        shopLock.lock(shopId);
        // 잠금 대기 중 마감이 지나거나 점유가 만료될 수 있으므로 잠금 뒤 시각으로 검사·가격·만료를 정한다
        LocalDateTime now = LocalDateTime.now(clock);
        availabilityService.validateAvailable(shopId, startsAt, treatment.getDurationMinutes());
        validateNoUserOverlap(loginUser.userId(), startsAt, endsAt, now);

        // 가격에 대한 견적을 시술 테이블에서 가져온다.
        PriceQuote quote = PriceCalculator.quote(treatment.getRegularPrice(),
                new SalePrices(treatment.getPrice(), treatment.getLastMinutePrice()), startsAt, now);

        Reservation reservation = Reservation.builder()
                .userId(loginUser.userId())
                .treatmentId(treatment.getTreatmentId())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .appliedPrice(quote.salePrice())
                .deposit(quote.depositAmount())
                .holdExpiresAt(SalePolicy.holdExpiresAt(now))
                .customerRequest(request.customerRequest())
                .build();
        return ReservationCreateResponse.from(reservationRepository.save(reservation));
    }

    /**
     * 결제 검증을 마친 결제 도메인이 같은 트랜잭션에서 호출해 점유를 예약 완료로 확정한다 (D-05).
     * 예약 행을 잠근 뒤 검사하므로 중복 확정·확정과 만료가 겹쳐도 한 번만 반영된다 (D-06).
     * 시간 점유는 이미 HOLD 가 잡고 있어 매장 잠금과 가용 시간 재검사는 하지 않는다.
     *
     * @param paidAmount 결제사에서 확인한 실제 결제 금액
     */
    @Transactional
    public Reservation confirmReservation(Long userId, Long reservationId, int paidAmount) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        // 잠금 대기 중 점유가 만료될 수 있으므로 잠금 뒤 시각으로 판단한다
        LocalDateTime now = LocalDateTime.now(clock);
        // 호출한 쪽이 예외를 잡고 트랜잭션을 이어가도 확정 상태가 남지 않도록 모든 검사를 마친 뒤 바꾼다
        reservation.validateConfirmable(paidAmount, now);
        // 점유가 유효하면 결제 마감(시작 15분 전) 전이지만, 정책 기준을 한 곳에서 확인하도록 함께 검사한다
        SalePolicy.validatePayable(reservation.getStartsAt(), now);
        reservation.confirm(paidAmount, now);
        return reservation;
    }

    /**
     * 만료 시각이 지난 점유를 EXPIRED 로 바꾸고 바뀐 건수를 반환한다 (D-07, D-08).
     * 빈자리는 가용 시간 계산이 만료된 점유를 빼고 보므로 따로 복원하지 않는다.
     * 주기 작업에서 반복 호출해도 안전하다.
     */
    @Transactional
    public int expireHolds() {
        return reservationRepository.expireHolds(LocalDateTime.now(clock));
    }

    /**
     * 한 사람이 같은 시간에 두 곳을 잡아 다른 고객의 빈자리를 막지 않도록, 매장과 관계없이 본인 예약과 겹치면 막는다
     */
    private void validateNoUserOverlap(Long userId, LocalDateTime startsAt, LocalDateTime endsAt,
            LocalDateTime now) {
        boolean overlapped = reservationRepository.findActiveOverlappingByUser(userId, startsAt, endsAt).stream()
                .anyMatch(reservation -> SalePolicy.isOccupying(
                        reservation.getStatus(), reservation.getHoldExpiresAt(), now));
        if (overlapped) {
            throw new BusinessException(ErrorCode.VACANCY_USER_TIME_OVERLAP);
        }
    }
}
