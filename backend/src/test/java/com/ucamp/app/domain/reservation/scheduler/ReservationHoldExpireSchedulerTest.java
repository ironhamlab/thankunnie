package com.ucamp.app.domain.reservation.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ucamp.app.domain.reservation.service.ReservationService;

@ExtendWith(MockitoExtension.class)
class ReservationHoldExpireSchedulerTest {

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationHoldExpireScheduler scheduler;

    @Test
    @DisplayName("스케줄은 예약 담당 점유 만료 메서드만 호출한다")
    void expireHolds_callsReservationService() {
        given(reservationService.expireHolds()).willReturn(2);

        scheduler.expireHolds();

        verify(reservationService).expireHolds();
    }
}
