package com.ucamp.app.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository.OwnerReservationNotice;

@ExtendWith(MockitoExtension.class)
class OwnerReservationNoticeServiceTest {

    private static final Long RESERVATION_ID = 100L;
    private static final Long SHOP_ID = 1L;
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final OwnerReservationNotice NOTICE =
            new OwnerReservationNotice(SHOP_ID, "김고객", "젤네일", STARTS_AT);

    @Mock
    private RecipientContactRepository recipientContactRepository;

    @Mock
    private NotificationInboxService notificationInboxService;

    private OwnerReservationNoticeService ownerReservationNoticeService;

    @BeforeEach
    void setUp() {
        ownerReservationNoticeService = new OwnerReservationNoticeService(
                recipientContactRepository, notificationInboxService);
    }

    @Test
    @DisplayName("예약이 확정되면 고객 이름·시술·시각으로 사장님 알림을 남긴다")
    void notifyConfirmed_savesShopInbox() {
        given(recipientContactRepository.findOwnerReservationNotice(RESERVATION_ID))
                .willReturn(Optional.of(NOTICE));

        ownerReservationNoticeService.notifyConfirmed(RESERVATION_ID);

        verify(notificationInboxService).saveForShop(
                SHOP_ID,
                NotificationInboxCategory.RESERVATION,
                "예약이 확정되었어요",
                "김고객 님 · 젤네일 · 11월 2일 10:00 예약이 확정되었습니다.",
                NotificationInboxService.ownerBookingsPath(RESERVATION_ID));
    }

    @Test
    @DisplayName("예약 정보가 없으면 사장님 확정 알림을 남기지 않는다")
    void notifyConfirmed_noReservation_skips() {
        given(recipientContactRepository.findOwnerReservationNotice(RESERVATION_ID)).willReturn(Optional.empty());

        ownerReservationNoticeService.notifyConfirmed(RESERVATION_ID);

        verify(notificationInboxService, never()).saveForShop(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("고객이 직접 취소해도 사장님 알림을 남긴다")
    void notifyCancelled_customer_savesShopInbox() {
        given(recipientContactRepository.findOwnerReservationNotice(RESERVATION_ID))
                .willReturn(Optional.of(NOTICE));

        ownerReservationNoticeService.notifyCancelled(RESERVATION_ID, CancelType.CUSTOMER);

        verify(notificationInboxService).saveForShop(
                SHOP_ID,
                NotificationInboxCategory.RESERVATION,
                "예약이 취소되었어요",
                "김고객 님 · 젤네일 · 11월 2일 10:00 예약이 취소되었습니다.",
                NotificationInboxService.ownerBookingsPath(RESERVATION_ID));
    }

    @Test
    @DisplayName("사장님이 매장 사정으로 취소한 건도 사장님 알림에 남긴다")
    void notifyCancelled_shopFault_savesShopInbox() {
        given(recipientContactRepository.findOwnerReservationNotice(RESERVATION_ID))
                .willReturn(Optional.of(NOTICE));

        ownerReservationNoticeService.notifyCancelled(RESERVATION_ID, CancelType.SHOP_FAULT);

        verify(notificationInboxService).saveForShop(
                SHOP_ID,
                NotificationInboxCategory.RESERVATION,
                "예약이 취소되었어요",
                "김고객 님 · 젤네일 · 11월 2일 10:00 예약이 매장 사정으로 취소되었습니다.",
                NotificationInboxService.ownerBookingsPath(RESERVATION_ID));
    }

    @Test
    @DisplayName("고객 요청 대리 취소도 사장님 알림에 남긴다")
    void notifyCancelled_customerRequest_savesShopInbox() {
        given(recipientContactRepository.findOwnerReservationNotice(RESERVATION_ID))
                .willReturn(Optional.of(NOTICE));

        ownerReservationNoticeService.notifyCancelled(RESERVATION_ID, CancelType.CUSTOMER_REQUEST);

        verify(notificationInboxService).saveForShop(
                SHOP_ID,
                NotificationInboxCategory.RESERVATION,
                "예약이 취소되었어요",
                "김고객 님 · 젤네일 · 11월 2일 10:00 고객 요청으로 예약이 취소되었습니다.",
                NotificationInboxService.ownerBookingsPath(RESERVATION_ID));
    }
}
