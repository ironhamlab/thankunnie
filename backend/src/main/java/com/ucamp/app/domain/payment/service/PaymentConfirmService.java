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
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
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
    private final ReservationRepository reservationRepository;

    public PaymentConfirmService(DepositPaymentRepository depositPaymentRepository,
                                 ReservationService reservationService,
                                 ReservationRepository reservationRepository) {
        this.depositPaymentRepository = depositPaymentRepository;
        this.reservationService = reservationService;
        this.reservationRepository = reservationRepository;
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
     * 자동 취소 실패로 남긴 UNKNOWN 기록은 확정된 결제가 아니므로 보지 않는다.
     * 다른 고객이 결제 고유번호와 예약 ID 만으로 남의 결제 정보를 받지 못하게 예약 주인을 확인한다.
     *
     * @throws BusinessException 다른 예약에 저장된 결제면 PAYMENT_RESERVATION_MISMATCH,
     *                           예약이 요청한 고객의 것이 아니면 RESERVATION_NOT_FOUND (예약 상세 조회와 같은 응답)
     */
    @Transactional(readOnly = true)
    public Optional<Long> findSaved(String impUid, Long reservationId, Long userId) {
        return depositPaymentRepository.findByPgTransactionKeyAndStatusNot(impUid, DepositPaymentStatus.UNKNOWN)
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

    /**
     * 예약을 확정하지 못해 자동 취소를 시도했지만 취소가 실패했거나 결과를 알 수 없는 결제를 UNKNOWN 으로 남긴다.
     * 돈은 빠져나갔을 수 있는데 확정 예약이 없으므로, 운영자가 DB 에서 찾아 포트원과 대조한 뒤 직접 처리한다.
     * 예약 행을 잠근 뒤 같은 결제 고유번호의 기록이 있는지 보므로, 같은 결제를 다시 확인해도 한 줄만 남는다.
     * 예약 주인을 확인한 뒤에만 나는 확정 오류에서 부르므로 예약은 항상 있다.
     *
     * @param reservationId 결제한 예약 ID
     * @param impUid        포트원 결제 고유번호
     * @param amount        결제 금액
     * @param approvedAt    결제 시각
     */
    @Transactional
    public void saveCancelUnknown(Long reservationId, String impUid, int amount, LocalDateTime approvedAt) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (depositPaymentRepository.existsByPgTransactionKey(impUid)) {
            return;
        }
        depositPaymentRepository.save(DepositPayment.builder()
                .reservation(reservation)
                .pgProvider(PG_PROVIDER)
                .pgTransactionKey(impUid)
                .status(DepositPaymentStatus.UNKNOWN)
                .amount(amount)
                .approvedAt(approvedAt)
                .build());
    }
}
