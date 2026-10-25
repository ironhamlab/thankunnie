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
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.reservation.dto.request.ReservationCreateRequest;
import com.ucamp.app.domain.reservation.dto.response.ReservationCreateResponse;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;
import com.ucamp.app.domain.vacancy.policy.PriceCalculator;
import com.ucamp.app.domain.vacancy.policy.PriceQuote;
import com.ucamp.app.domain.vacancy.policy.SalePolicy;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TreatmentRepository treatmentRepository;
    private final AvailabilityService availabilityService;
    private final ShopLock shopLock;
    private final Clock clock;

    public ReservationService(ReservationRepository reservationRepository,
            TreatmentRepository treatmentRepository, AvailabilityService availabilityService,
            ShopLock shopLock, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.treatmentRepository = treatmentRepository;
        this.availabilityService = availabilityService;
        this.shopLock = shopLock;
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
                .filter(Treatment::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.TREATMENT_NOT_FOUND));
        LocalDateTime endsAt = startsAt.plusMinutes(treatment.getDurationMinutes());

        // 같은 시간에 두 고객이 동시에 클릭해도 한 명만 점유하도록 매장 단위로 순서를 맞춘다
        shopLock.lock(treatment.getShopId());
        // 잠금 대기 중 마감이 지나거나 점유가 만료될 수 있으므로 잠금 뒤 시각으로 검사·가격·만료를 정한다
        LocalDateTime now = LocalDateTime.now(clock);
        availabilityService.validateAvailable(treatment.getShopId(), startsAt, treatment.getDurationMinutes());

        PriceQuote quote = PriceCalculator.quote(
                treatment.getRegularPrice(), treatment.salePrices(), startsAt, now);

        Reservation reservation = Reservation.builder()
                .userId(loginUser.userId())
                .treatmentId(treatment.getId())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .appliedPrice(quote.salePrice())
                .deposit(quote.depositAmount())
                .holdExpiresAt(SalePolicy.holdExpiresAt(now))
                .customerRequest(request.customerRequest())
                .build();
        return ReservationCreateResponse.from(reservationRepository.save(reservation));
    }
}
