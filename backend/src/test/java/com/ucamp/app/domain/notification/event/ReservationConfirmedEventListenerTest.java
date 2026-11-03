package com.ucamp.app.domain.notification.event;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ucamp.app.domain.notification.service.OwnerReservationNoticeService;
import com.ucamp.app.domain.notification.service.ReservationSmsService;

@ExtendWith(MockitoExtension.class)
class ReservationConfirmedEventListenerTest {

    private static final Long RESERVATION_ID = 100L;

    @Mock
    private ReservationSmsService reservationSmsService;

    @Mock
    private OwnerReservationNoticeService ownerReservationNoticeService;

    private ReservationConfirmedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ReservationConfirmedEventListener(reservationSmsService, ownerReservationNoticeService);
    }

    @Test
    @DisplayName("예약 확정 문자가 실패해도 사장님 알림은 남긴다")
    void handle_smsFails_stillNotifiesOwner() {
        doThrow(new RuntimeException("문자 실패")).when(reservationSmsService).send(RESERVATION_ID);

        listener.handle(new ReservationConfirmedEvent(RESERVATION_ID));

        verify(ownerReservationNoticeService).notifyConfirmed(RESERVATION_ID);
    }

    @Test
    @DisplayName("사장님 알림 저장이 실패해도 예외를 밖으로 던지지 않는다")
    void handle_ownerNoticeFails_doesNotThrow() {
        doThrow(new RuntimeException("저장 실패")).when(ownerReservationNoticeService).notifyConfirmed(RESERVATION_ID);

        listener.handle(new ReservationConfirmedEvent(RESERVATION_ID));

        verify(reservationSmsService).send(RESERVATION_ID);
    }
}
