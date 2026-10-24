package com.ucamp.app.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.reservation.dto.response.ReservationDetailResponse;
import com.ucamp.app.domain.reservation.dto.response.ReservationSummaryResponse;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationQueryRepository;
import com.ucamp.app.domain.reservation.repository.ReservationSearch;
import com.ucamp.app.domain.reservation.repository.ReservationView;

class ReservationQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);
    private static final AuthUser OWNER = new AuthUser(11L, UserRole.OWNER, 1L);
    private static final AuthUser CUSTOMER = new AuthUser(1L, UserRole.CUSTOMER, null);

    private ReservationQueryRepository reservationQueryRepository;
    private ReservationQueryService reservationQueryService;

    @BeforeEach
    void setUp() {
        reservationQueryRepository = mock(ReservationQueryRepository.class);
        Clock clock = Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);
        reservationQueryService = new ReservationQueryService(reservationQueryRepository, clock);
    }

    @Test
    @DisplayName("사장님 목록은 본인 매장·만료 제외·날짜 경계로 조회하고 페이지 정보를 채운다")
    void getOwnerReservations_defaultFilter_searchesOwnShop() {
        given(reservationQueryRepository.count(any())).willReturn(21L);
        given(reservationQueryRepository.search(any(), eq(20), eq(20)))
                .willReturn(List.of(view(ReservationStatus.CONFIRMED, NOW.plusHours(2))));

        PageResponse<ReservationSummaryResponse> result = reservationQueryService.getOwnerReservations(
                OWNER, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), null, 1, 20);

        ArgumentCaptor<ReservationSearch> captor = ArgumentCaptor.forClass(ReservationSearch.class);
        verify(reservationQueryRepository).count(captor.capture());
        ReservationSearch search = captor.getValue();
        assertThat(search.shopId()).isEqualTo(1L);
        assertThat(search.userId()).isNull();
        assertThat(search.statuses()).doesNotContain(ReservationStatus.EXPIRED).contains(ReservationStatus.HOLD);
        assertThat(search.startsFrom()).isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 0));
        assertThat(search.startsBefore()).isEqualTo(LocalDateTime.of(2026, 11, 4, 0, 0));
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.content().get(0).balance()).isEqualTo(10000);
    }

    @Test
    @DisplayName("확정 예약은 시작 전에만 취소 가능으로 표시한다")
    void getOwnerReservations_cancellable_onlyConfirmedBeforeStart() {
        given(reservationQueryRepository.search(any(), anyInt(), anyInt())).willReturn(List.of(
                view(ReservationStatus.CONFIRMED, NOW.plusMinutes(30)),
                view(ReservationStatus.CONFIRMED, NOW),
                view(ReservationStatus.CANCEL_PENDING, NOW.plusHours(1))));

        PageResponse<ReservationSummaryResponse> result =
                reservationQueryService.getOwnerReservations(OWNER, null, null, null, 0, 20);

        assertThat(result.content()).extracting(ReservationSummaryResponse::cancellable)
                .containsExactly(true, false, false);
    }

    @Test
    @DisplayName("사장님이 아니면 403 예외가 난다")
    void getOwnerReservations_customer_throwsForbidden() {
        assertThatThrownBy(() -> reservationQueryService.getOwnerReservations(CUSTOMER, null, null, null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(reservationQueryRepository);
    }

    @Test
    @DisplayName("페이지 크기가 범위를 벗어나거나 to 가 from 보다 앞서면 400 예외가 난다")
    void getOwnerReservations_invalidParams_throwsInvalidInput() {
        assertThatThrownBy(() -> reservationQueryService.getOwnerReservations(OWNER, null, null, null, 0, 101))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> reservationQueryService.getOwnerReservations(OWNER, null, null, null, -1, 20))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> reservationQueryService.getOwnerReservations(OWNER,
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 2), null, 0, 20))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("다른 매장 예약 상세는 404 예외가 난다")
    void getOwnerReservation_otherShop_throwsNotFound() {
        given(reservationQueryRepository.findOne(99L, 1L, null)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reservationQueryService.getOwnerReservation(OWNER, 99L))
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("상세는 결제가 있으면 결제 정보를, 취소가 없으면 null 을 담는다")
    void getOwnerReservation_paid_includesPayment() {
        given(reservationQueryRepository.findOne(1L, 1L, null))
                .willReturn(Optional.of(view(ReservationStatus.CONFIRMED, NOW.plusHours(2))));

        ReservationDetailResponse result = reservationQueryService.getOwnerReservation(OWNER, 1L);

        assertThat(result.payment().status()).isEqualTo("PAID");
        assertThat(result.payment().amount()).isEqualTo(20000);
        assertThat(result.cancel()).isNull();
        assertThat(result.summary().startsAt().getOffset()).isEqualTo(java.time.ZoneOffset.ofHours(9));
    }

    private static ReservationView view(ReservationStatus status, LocalDateTime startsAt) {
        return new ReservationView(1L, status, startsAt, startsAt.plusHours(1), 30000, 20000,
                null, NOW.minusDays(1), null, NOW.minusDays(1),
                1L, "언니네 네일", 1L, "젤네일", 1L, "고객일", "010-0000-0001",
                "PAID", 20000, 0, NOW.minusDays(1), null,
                null, null, null, null, null, null);
    }
}
