package com.ucamp.app.domain.reservation.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.reservation.dto.response.ReservationDetailResponse;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationQueryRepository;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.reservation.repository.ReservationView;

/**
 * 매장 계정의 노쇼 표시·해제. 시술이 끝난 확정 예약만 표시할 수 있고, 해제는 기한 없이 할 수 있다.
 * 예약 상태와 예약금·결제는 그대로 두며, 노쇼 예약은 이용 횟수·이용 추정 금액·피크 집계에서 빠진다.
 * 같은 상태로 다시 요청하면 바꾸지 않고 현재 예약을 돌려준다.
 */
@Service
public class ReservationNoShowService {

    private final ReservationRepository reservationRepository;
    private final ReservationQueryRepository reservationQueryRepository;
    private final Clock clock;

    public ReservationNoShowService(ReservationRepository reservationRepository,
            ReservationQueryRepository reservationQueryRepository, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.reservationQueryRepository = reservationQueryRepository;
        this.clock = clock;
    }

    @Transactional
    public ReservationDetailResponse markNoShow(AuthUser loginUser, Long reservationId) {
        return change(loginUser, reservationId, true);
    }

    @Transactional
    public ReservationDetailResponse unmarkNoShow(AuthUser loginUser, Long reservationId) {
        return change(loginUser, reservationId, false);
    }

    private ReservationDetailResponse change(AuthUser loginUser, Long reservationId, boolean noShow) {
        loginUser.requireRole(UserRole.OWNER);
        LocalDateTime now = LocalDateTime.now(clock);
        ReservationView view = findOwned(loginUser, reservationId);
        if (view.noShow() != noShow) {
            validate(view, noShow, now);
            int updated = reservationRepository.changeNoShow(
                    reservationId, !noShow, noShow, ReservationStatus.CONFIRMED, now);
            if (updated == 0) {
                // 그사이 다른 요청이 먼저 바꿨다. 이미 원하는 상태면 성공으로 보고, 아니면 바뀐 상태 기준으로 다시 거절한다
                view = findOwned(loginUser, reservationId);
                if (view.noShow() != noShow) {
                    validate(view, noShow, now);
                    throw new BusinessException(ErrorCode.RESERVATION_NOT_CONFIRMED);
                }
            } else {
                view = findOwned(loginUser, reservationId);
            }
        }
        // 노쇼를 바꿀 수 있는 예약은 이미 시작한 예약이라 취소 버튼은 보이지 않는다
        return ReservationDetailResponse.from(view, false, now);
    }

    // 다른 매장 예약은 없는 것으로 본다
    private ReservationView findOwned(AuthUser loginUser, Long reservationId) {
        return reservationQueryRepository.findOne(reservationId, loginUser.shopId(), null)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static void validate(ReservationView view, boolean noShow, LocalDateTime now) {
        if (view.status() != ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_CONFIRMED);
        }
        if (noShow && view.endsAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_ENDED);
        }
    }
}
