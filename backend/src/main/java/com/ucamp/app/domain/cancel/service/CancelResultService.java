package com.ucamp.app.domain.cancel.service;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.cancel.dto.response.CancelResultResponse;
import com.ucamp.app.domain.cancel.entity.ReservationCancel;
import com.ucamp.app.domain.cancel.repository.ReservationCancelRepository;
import com.ucamp.app.domain.notification.event.SlotReopenedEvent;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.schedule.repository.ShopScheduleExceptionRepository;

/**
 * 결제 취소 결과 반영 (E-06·E-07). 결제 취소(PG 호출)가 끝난 뒤 별도 트랜잭션으로 취소·예약 상태를 바꾼다.
 */
@Service
@Transactional(readOnly = true)
public class CancelResultService {

    private final ReservationCancelRepository reservationCancelRepository;
    private final ReservationRepository reservationRepository;
    private final ShopScheduleExceptionRepository shopScheduleExceptionRepository;
    private final GraceCancelUsageService graceCancelUsageService;
    private final ApplicationEventPublisher eventPublisher;

    public CancelResultService(ReservationCancelRepository reservationCancelRepository,
            ReservationRepository reservationRepository,
            ShopScheduleExceptionRepository shopScheduleExceptionRepository,
            GraceCancelUsageService graceCancelUsageService, ApplicationEventPublisher eventPublisher) {
        this.reservationCancelRepository = reservationCancelRepository;
        this.reservationRepository = reservationRepository;
        this.shopScheduleExceptionRepository = shopScheduleExceptionRepository;
        this.graceCancelUsageService = graceCancelUsageService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 결제 취소 성공(또는 환불액 0원). 취소 완료, 예약 취소 완료 (CNL-21, REO-03).
     * 취소하며 닫지 않았으면 그 시간은 계산에서 자동으로 다시 열리므로 재공개 이벤트를 발행한다 (REO-01·06).
     * 알림 쪽 리스너는 이 트랜잭션이 커밋된 뒤 실행되고, 실패해도 취소는 되돌리지 않는다.
     */
    @Transactional
    public CancelResultResponse complete(Long cancelId, LocalDateTime now) {
        ReservationCancel cancel = find(cancelId);
        cancel.complete(null, now);
        changeReservationStatus(cancel, ReservationStatus.CANCELLED, now);
        if (!shopScheduleExceptionRepository.existsByReservationId(cancel.getReservationId())) {
            eventPublisher.publishEvent(new SlotReopenedEvent(cancel.getReservationId()));
        }
        return CancelResultResponse.of(cancel, ReservationStatus.CANCELLED);
    }

    /**
     * 결제 취소 확실히 실패. 예약을 확정으로 되돌리고 무료 취소 기록과 취소하며 등록한 닫기를 지운다.
     * 같은 행으로 재시도할 수 있다 (CNL-23).
     */
    @Transactional
    public void fail(Long cancelId, LocalDateTime now) {
        ReservationCancel cancel = find(cancelId);
        cancel.fail();
        graceCancelUsageService.release(cancelId);
        shopScheduleExceptionRepository.deleteByReservationId(cancel.getReservationId());
        changeReservationStatus(cancel, ReservationStatus.CONFIRMED, now);
    }

    /**
     * 결제 취소 결과 불명. 실제로 환불됐을 수 있으므로 예약은 취소 처리 중으로 두어 시간을 계속 막는다 (CNL-24).
     */
    @Transactional
    public CancelResultResponse markUnknown(Long cancelId) {
        ReservationCancel cancel = find(cancelId);
        cancel.markUnknown();
        return CancelResultResponse.of(cancel, ReservationStatus.CANCEL_PENDING);
    }

    private ReservationCancel find(Long cancelId) {
        return reservationCancelRepository.findById(cancelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CANCEL_RESERVATION_NOT_FOUND));
    }

    // 취소 접수에서 CANCEL_PENDING 으로 바꿔 두었으므로, 다른 상태면 데이터가 어긋난 것이다
    private void changeReservationStatus(ReservationCancel cancel, ReservationStatus to, LocalDateTime now) {
        int changed = reservationRepository.changeStatus(cancel.getReservationId(),
                ReservationStatus.CANCEL_PENDING, to, now);
        if (changed == 0) {
            throw new IllegalStateException("예약 " + cancel.getReservationId() + " 이 취소 처리 중 상태가 아닙니다");
        }
    }
}
