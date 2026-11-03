package com.ucamp.app.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationQueryRepository;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.reservation.repository.ReservationView;

class ReservationNoShowServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 15, 0);
    private static final AuthUser OWNER = new AuthUser(11L, UserRole.OWNER, 1L);

    private ReservationRepository reservationRepository;
    private ReservationQueryRepository reservationQueryRepository;
    private ReservationNoShowService service;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        reservationQueryRepository = mock(ReservationQueryRepository.class);
        Clock clock = Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);
        service = new ReservationNoShowService(reservationRepository, reservationQueryRepository, clock);
    }

    @Test
    @DisplayName("끝난 확정 예약을 노쇼로 표시하고 바뀐 예약을 돌려준다")
    void markNoShow_ended_marks() {
        given(reservationQueryRepository.findOne(7L, 1L, null))
                .willReturn(Optional.of(view(ReservationStatus.CONFIRMED, NOW.minusHours(1), false)))
                .willReturn(Optional.of(view(ReservationStatus.CONFIRMED, NOW.minusHours(1), true)));
        given(reservationRepository.changeNoShow(7L, false, true, ReservationStatus.CONFIRMED, NOW)).willReturn(1);

        assertThat(service.markNoShow(OWNER, 7L).summary().noShow()).isTrue();
    }

    @Test
    @DisplayName("이미 노쇼면 바꾸지 않고 그대로 돌려준다")
    void markNoShow_already_noChange() {
        given(reservationQueryRepository.findOne(7L, 1L, null))
                .willReturn(Optional.of(view(ReservationStatus.CONFIRMED, NOW.minusHours(1), true)));

        assertThat(service.markNoShow(OWNER, 7L).summary().noShow()).isTrue();
        verify(reservationRepository, never()).changeNoShow(any(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("끝나지 않은 예약은 RESERVATION_NOT_ENDED, 확정이 아니면 RESERVATION_NOT_CONFIRMED 이다")
    void markNoShow_invalid_throws() {
        given(reservationQueryRepository.findOne(7L, 1L, null))
                .willReturn(Optional.of(view(ReservationStatus.CONFIRMED, NOW.plusMinutes(30), false)));
        given(reservationQueryRepository.findOne(8L, 1L, null))
                .willReturn(Optional.of(view(ReservationStatus.CANCELLED, NOW.minusDays(1), false)));

        assertThatThrownBy(() -> service.markNoShow(OWNER, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_NOT_ENDED);
        assertThatThrownBy(() -> service.markNoShow(OWNER, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_NOT_CONFIRMED);
        verify(reservationRepository, never()).changeNoShow(any(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("동시에 다른 요청이 먼저 같은 상태로 바꿨으면 성공으로 본다")
    void markNoShow_concurrentSameResult_succeeds() {
        given(reservationQueryRepository.findOne(7L, 1L, null))
                .willReturn(Optional.of(view(ReservationStatus.CONFIRMED, NOW.minusHours(1), false)))
                .willReturn(Optional.of(view(ReservationStatus.CONFIRMED, NOW.minusHours(1), true)));
        given(reservationRepository.changeNoShow(7L, false, true, ReservationStatus.CONFIRMED, NOW)).willReturn(0);

        assertThat(service.markNoShow(OWNER, 7L).summary().noShow()).isTrue();
    }

    @Test
    @DisplayName("노쇼 해제는 기한 없이 된다")
    void unmarkNoShow_longAfter_unmarks() {
        given(reservationQueryRepository.findOne(7L, 1L, null))
                .willReturn(Optional.of(view(ReservationStatus.CONFIRMED, NOW.minusDays(90), true)))
                .willReturn(Optional.of(view(ReservationStatus.CONFIRMED, NOW.minusDays(90), false)));
        given(reservationRepository.changeNoShow(7L, true, false, ReservationStatus.CONFIRMED, NOW)).willReturn(1);

        assertThat(service.unmarkNoShow(OWNER, 7L).summary().noShow()).isFalse();
    }

    @Test
    @DisplayName("이미 노쇼가 아니어도 확정이 아닌 예약의 해제는 RESERVATION_NOT_CONFIRMED 이다")
    void unmarkNoShow_cancelledSameState_throws() {
        given(reservationQueryRepository.findOne(8L, 1L, null))
                .willReturn(Optional.of(view(ReservationStatus.CANCELLED, NOW.plusDays(1), false)));

        assertThatThrownBy(() -> service.unmarkNoShow(OWNER, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_NOT_CONFIRMED);
        verify(reservationRepository, never()).changeNoShow(any(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("시작 전 확정 예약에 해제를 다시 보내면 바꾸지 않고 취소 가능으로 돌려준다")
    void unmarkNoShow_upcomingSameState_keepsCancellable() {
        given(reservationQueryRepository.findOne(7L, 1L, null))
                .willReturn(Optional.of(view(ReservationStatus.CONFIRMED, NOW.plusHours(2), false)));

        assertThat(service.unmarkNoShow(OWNER, 7L).summary().cancellable()).isTrue();
        verify(reservationRepository, never()).changeNoShow(any(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("다른 매장 예약은 404, 매장 계정이 아니면 403 이다")
    void scope_and_role() {
        given(reservationQueryRepository.findOne(9L, 1L, null)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.markNoShow(OWNER, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        ReservationNoShowService fresh = new ReservationNoShowService(mock(ReservationRepository.class),
                mock(ReservationQueryRepository.class), Clock.systemDefaultZone());
        assertThatThrownBy(() -> fresh.markNoShow(new AuthUser(1L, UserRole.CUSTOMER, null), 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    private static ReservationView view(ReservationStatus status, LocalDateTime endsAt, boolean noShow) {
        LocalDateTime startsAt = endsAt.minusHours(1);
        return new ReservationView(7L, status, startsAt, endsAt, 30000, 20000,
                startsAt.minusDays(3), startsAt.minusDays(3), null, startsAt.minusDays(3),
                1L, "언니네 네일", 1L, "젤네일", 1L, "고객일", "010-1111-1111",
                "PAID", 20000, 0, startsAt.minusDays(3), null,
                null, null, null, null, null, null, noShow);
    }
}
