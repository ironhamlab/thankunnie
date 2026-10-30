package com.ucamp.app.domain.payment.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.payment.entity.DepositPayment;
import com.ucamp.app.domain.payment.entity.DepositPaymentStatus;
import com.ucamp.app.domain.payment.repository.DepositPaymentRepository;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.service.ReservationService;

/**
 * 확인된 결제로 예약을 확정하고 예약금결제를 저장하는 짧은 트랜잭션. PG 조회·취소는 {@link PaymentService} 가 트랜잭션 밖에서 한다.
 *
 * <p>같은 클래스 안에서 호출하면 {@code @Transactional} 이 적용되지 않으므로 별도 빈으로 분리했다.
 */
@Service
public class PaymentConfirmService {

    static final String PG_PROVIDER = "PORTONE";

    private final DepositPaymentRepository depositPaymentRepository;
    private final ReservationService reservationService;

    public PaymentConfirmService(DepositPaymentRepository depositPaymentRepository,
                                 ReservationService reservationService) {
        this.depositPaymentRepository = depositPaymentRepository;
        this.reservationService = reservationService;
    }

    /**
     * 예약을 확정(CONFIRMED)하고 예약금결제를 PAID 로 저장한다. 확정 검사(점유 만료·금액·결제 마감)는 예약 쪽이 한다.
     * 같은 결제가 이미 저장돼 있으면 다시 확정하지 않고 저장된 결제 ID 를 돌려준다.
     *
     * @return 예약금결제 ID
     * @throws BusinessException 같은 결제가 다른 예약에 이미 쓰였으면 PAYMENT_RESERVATION_MISMATCH,
     *                           저장된 결제의 예약이 요청한 고객의 것이 아니면 RESERVATION_NOT_FOUND, 그 밖에는 예약 확정 오류
     */
    @Transactional
    public Long confirm(Long userId, Long reservationId, String impUid, int paidAmount,
                        String customerRequest, LocalDateTime approvedAt) {
        Optional<Long> saved = findSaved(impUid, reservationId, userId);
        if (saved.isPresent()) {
            return saved.get();
        }
        // 예약 행을 잠그고 확정하므로 같은 예약을 동시에 확정해도 한쪽만 성공한다
        Reservation reservation = reservationService.confirmReservation(
                userId, reservationId, paidAmount, customerRequest);
        DepositPayment payment = DepositPayment.builder()
                .reservation(reservation)
                .pgProvider(PG_PROVIDER)
                .pgTransactionKey(impUid)
                .status(DepositPaymentStatus.PAID)
                .amount(paidAmount)
                .approvedAt(approvedAt)
                .build();
        return depositPaymentRepository.save(payment).getDepositPaymentId();
    }

    /**
     * 이 결제가 이 고객의 이 예약으로 이미 저장돼 있으면 예약금결제 ID 를 돌려준다.
     * 다른 고객이 결제 고유번호와 예약 ID 만으로 남의 결제 정보를 받지 못하게 예약 주인을 확인한다.
     *
     * @throws BusinessException 다른 예약에 저장된 결제면 PAYMENT_RESERVATION_MISMATCH,
     *                           예약이 요청한 고객의 것이 아니면 RESERVATION_NOT_FOUND (예약 상세 조회와 같은 응답)
     */
    @Transactional(readOnly = true)
    public Optional<Long> findSaved(String impUid, Long reservationId, Long userId) {
        return depositPaymentRepository.findByPgTransactionKey(impUid)
                .map(payment -> {
                    Reservation reservation = payment.getReservation();
                    if (!reservation.getId().equals(reservationId)) {
                        throw new BusinessException(ErrorCode.PAYMENT_RESERVATION_MISMATCH);
                    }
                    if (!reservation.getUserId().equals(userId)) {
                        throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
                    }
                    return payment.getDepositPaymentId();
                });
    }
}
