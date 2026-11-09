package com.ucamp.app.domain.notification.event;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.notification.service.CancelSmsService;
import com.ucamp.app.domain.notification.service.OwnerReservationNoticeService;

@ExtendWith(MockitoExtension.class)
class CancelCompletedEventListenerTest {

    private static final Long RESERVATION_ID = 100L;

    @Mock
    private CancelSmsService cancelSmsService;

    @Mock
    private OwnerReservationNoticeService ownerReservationNoticeService;

    private CancelCompletedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new CancelCompletedEventListener(cancelSmsService, ownerReservationNoticeService);
    }

    @Test
    @DisplayName("취소 문자가 실패해도 사장님 알림은 남긴다")
    void handle_smsFails_stillNotifiesOwner() {
        doThrow(new RuntimeException("문자 실패"))
                .when(cancelSmsService).send(RESERVATION_ID, CancelType.SHOP_FAULT);

        listener.handle(new CancelCompletedEvent(RESERVATION_ID, CancelType.SHOP_FAULT));

        verify(ownerReservationNoticeService).notifyCancelled(RESERVATION_ID, CancelType.SHOP_FAULT);
    }

    @Test
    @DisplayName("사장님 알림 저장이 실패해도 예외를 밖으로 던지지 않는다")
    void handle_ownerNoticeFails_doesNotThrow() {
        doThrow(new RuntimeException("저장 실패"))
                .when(ownerReservationNoticeService).notifyCancelled(RESERVATION_ID, CancelType.CUSTOMER);

        listener.handle(new CancelCompletedEvent(RESERVATION_ID, CancelType.CUSTOMER));

        verify(cancelSmsService).send(RESERVATION_ID, CancelType.CUSTOMER);
    }
}
