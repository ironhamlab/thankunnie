package com.ucamp.app.domain.reservation.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationDetailResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationSummaryResponse;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationQueryRepository;
import com.ucamp.app.domain.reservation.repository.ReservationSearch;
import com.ucamp.app.domain.reservation.repository.ReservationView;

/**
 * 역할별 예약 조회 (B-09 고객 내 예약, B-10 사장님 예약 현황). 관리자 조회(B-11)도 같은 조회를 범위만 바꿔 쓴다.
 */
@Service
@Transactional(readOnly = true)
public class ReservationQueryService {

    // 결제하지 않고 끝난 점유는 기본 목록에서 뺀다. 상태 필터로 따로 볼 수 있다
    private static final Set<ReservationStatus> DEFAULT_STATUSES =
            EnumSet.complementOf(EnumSet.of(ReservationStatus.EXPIRED));
    private static final int MAX_PAGE_SIZE = 100;

    private final ReservationQueryRepository reservationQueryRepository;
    private final Clock clock;

    public ReservationQueryService(ReservationQueryRepository reservationQueryRepository, Clock clock) {
        this.reservationQueryRepository = reservationQueryRepository;
        this.clock = clock;
    }

    /**
     * 본인 매장 예약 목록. from~to 는 시작 날짜 기준(둘 다 포함), 시작 시각 순.
     */
    public PageResponse<ReservationSummaryResponse> getOwnerReservations(AuthUser loginUser, LocalDate from,
            LocalDate to, List<ReservationStatus> statuses, int page, int size) {
        loginUser.requireRole(UserRole.OWNER);
        ReservationSearch search = toSearch(loginUser.shopId(), null, from, to, statuses);
        return findPage(search, page, size);
    }

    /**
     * 본인 매장 예약 상세. 다른 매장 예약은 없는 것으로 본다.
     */
    public ReservationDetailResponse getOwnerReservation(AuthUser loginUser, Long reservationId) {
        loginUser.requireRole(UserRole.OWNER);
        ReservationView view = reservationQueryRepository.findOne(reservationId, loginUser.shopId(), null)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return ReservationDetailResponse.from(view, isCancellable(view), LocalDateTime.now(clock));
    }

    /**
     * 고객 본인 예약 목록 (B-09). 여러 매장 예약을 시작 시각 순으로 모아 보여준다. 필터는 사장님 목록과 같다.
     */
    public PageResponse<ReservationSummaryResponse> getMyReservations(AuthUser loginUser, LocalDate from,
            LocalDate to, List<ReservationStatus> statuses, int page, int size) {
        loginUser.requireRole(UserRole.CUSTOMER);
        ReservationSearch search = toSearch(null, loginUser.userId(), from, to, statuses);
        return findPage(search, page, size);
    }

    /**
     * 고객 본인 예약 상세 (B-09). 다른 고객 예약은 없는 것으로 본다.
     */
    public ReservationDetailResponse getMyReservation(AuthUser loginUser, Long reservationId) {
        loginUser.requireRole(UserRole.CUSTOMER);
        ReservationView view = reservationQueryRepository.findOne(reservationId, null, loginUser.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return ReservationDetailResponse.from(view, isCancellable(view), LocalDateTime.now(clock));
    }

    private PageResponse<ReservationSummaryResponse> findPage(ReservationSearch search, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "page 는 0 이상, size 는 1~100 이어야 합니다.");
        }
        long total = reservationQueryRepository.count(search);
        // 큰 page 에서 int 곱셈이 넘쳐 음수 offset 이 되면 Oracle 이 0 으로 보고 첫 페이지를 돌려준다
        long offset = (long) page * size;
        List<ReservationSummaryResponse> content = reservationQueryRepository.search(search, offset, size)
                .stream()
                .map(view -> ReservationSummaryResponse.from(view, isCancellable(view)))
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    private static ReservationSearch toSearch(Long shopId, Long userId, LocalDate from, LocalDate to,
            List<ReservationStatus> statuses) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "to 는 from 과 같거나 이후 날짜여야 합니다.");
        }
        Set<ReservationStatus> statusSet = statuses == null || statuses.isEmpty()
                ? DEFAULT_STATUSES
                : EnumSet.copyOf(statuses);
        return new ReservationSearch(shopId, userId, statusSet,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay());
    }

    // 취소 버튼 표시용. 확정 상태이고 시작 전이면 보여준다. 실제 취소 가능 판단은 취소 API(E)가 한다
    private boolean isCancellable(ReservationView view) {
        return view.status() == ReservationStatus.CONFIRMED && LocalDateTime.now(clock).isBefore(view.startsAt());
    }
}
