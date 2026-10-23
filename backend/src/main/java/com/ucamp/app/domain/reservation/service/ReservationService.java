package com.ucamp.app.domain.reservation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.policy.ReservationStatus;
import com.ucamp.app.domain.reservation.dto.request.ReservationCreateRequest;
import com.ucamp.app.domain.reservation.dto.response.ReservationCreateResponse;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;
import com.ucamp.app.domain.vacancy.policy.PriceCalculator;
import com.ucamp.app.domain.vacancy.policy.PriceQuote;
import com.ucamp.app.domain.vacancy.policy.SalePolicy;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    // 시간을 잡고 있을 수 있는 상태. HOLD 는 만료 여부를 SalePolicy.isOccupying 으로 한 번 더 본다
    private static final Set<ReservationStatus> OCCUPYING_STATUSES = EnumSet.of(
            ReservationStatus.HOLD, ReservationStatus.CONFIRMED, ReservationStatus.CANCEL_PENDING);

    private final ReservationRepository reservationRepository;
    private final TreatmentRepository treatmentRepository;
    private final Clock clock;

    public ReservationService(ReservationRepository reservationRepository,
            TreatmentRepository treatmentRepository, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.treatmentRepository = treatmentRepository;
        this.clock = clock;
    }

    /**
     * 예약 클릭 시 점유(HOLD) 예약을 만든다. 가격 구간과 예약금은 이 시점 기준으로 고정한다.
     */
    @Transactional
    public ReservationCreateResponse createReservation(AuthUser loginUser, ReservationCreateRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);

        // 헌재 시각 clock으로 고정
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startsAt = request.startsAt().atZoneSameInstant(BookingPolicy.ZONE).toLocalDateTime();
        SalePolicy.validateBookable(startsAt, now);

        Treatment treatment = treatmentRepository.findById(request.treatmentId())
                .filter(Treatment::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.TREATMENT_NOT_FOUND));
        LocalDateTime endsAt = startsAt.plusMinutes(treatment.getDurationMinutes());

        // 같은 시간에 두 고객이 동시에 클릭해도 한 명만 점유하도록 매장 단위로 순서를 맞춘다
        reservationRepository.lockShop(treatment.getShopId());
        validateNotTaken(treatment.getShopId(), startsAt, endsAt, now);

        PriceQuote quote = PriceCalculator.quote(
                treatment.getRegularPrice(), treatment.salePrices(), startsAt, now);
        Reservation reservation = Reservation.builder()
                .userId(loginUser.userId())
                .treatment(treatment)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .appliedPrice(quote.salePrice())
                .deposit(quote.depositAmount())
                .holdExpiresAt(SalePolicy.holdExpiresAt(now))
                .customerRequest(request.customerRequest())
                .build();
        return ReservationCreateResponse.from(reservationRepository.save(reservation));
    }

    private void validateNotTaken(Long shopId, LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
        boolean taken = reservationRepository.findOverlappingInShop(shopId, startsAt, endsAt, OCCUPYING_STATUSES)
                .stream()
                .anyMatch(reservation -> SalePolicy.isOccupying(
                        reservation.getStatus(), reservation.getHoldExpiresAt(), now));
        if (taken) {
            throw new BusinessException(ErrorCode.VACANCY_TIME_TAKEN);
        }
    }
}
