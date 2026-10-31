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
import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository.CancelSmsInfo;

@ExtendWith(MockitoExtension.class)
class CancelSmsServiceTest {

    private static final Long RESERVATION_ID = 100L;
    private static final Long USER_ID = 7L;
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private RecipientContactRepository recipientContactRepository;

    @Mock
    private SolapiSmsClient solapiSmsClient;

    @Mock
    private NotificationInboxService notificationInboxService;

    private CancelSmsService cancelSmsService;

    @BeforeEach
    void setUp() {
        cancelSmsService = new CancelSmsService(
                recipientContactRepository, solapiSmsClient, notificationInboxService);
    }

    @Test
    @DisplayName("고객이 직접 취소하면 매장·예약 시각이 담긴 문자를 보낸다")
    void send_customerCancel_sendsCustomerMessage() {
        given(recipientContactRepository.findCancelSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo(USER_ID, "010-1234-5678", "예쁜네일", STARTS_AT)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.success("G-1"));

        cancelSmsService.send(RESERVATION_ID, CancelType.CUSTOMER);

        verify(solapiSmsClient).send("010-1234-5678",
                "[땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 취소되었습니다.");
        verify(notificationInboxService).save(
                USER_ID,
                NotificationInboxCategory.RESERVATION,
                "예약이 취소되었어요",
                "[땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 취소되었습니다.",
                NotificationInboxService.MY_RESERVATIONS_PATH);
    }

    @Test
    @DisplayName("사장님이 매장 사유로 취소하면 매장 사정 문구로 보낸다")
    void send_shopFault_sendsOwnerMessage() {
        given(recipientContactRepository.findCancelSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo(USER_ID, "010-1234-5678", "예쁜네일", STARTS_AT)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.success("G-1"));

        cancelSmsService.send(RESERVATION_ID, CancelType.SHOP_FAULT);

        verify(solapiSmsClient).send("010-1234-5678",
                "[땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 매장 사정으로 취소되었습니다.");
        verify(notificationInboxService).save(
                USER_ID,
                NotificationInboxCategory.RESERVATION,
                "예약이 취소되었어요",
                "[땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 매장 사정으로 취소되었습니다.",
                NotificationInboxService.MY_RESERVATIONS_PATH);
    }

    @Test
    @DisplayName("사장님이 고객 요청으로 대리 취소하면 고객 요청 문구로 보낸다")
    void send_customerRequest_sendsCustomerRequestMessage() {
        given(recipientContactRepository.findCancelSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo(USER_ID, "010-1234-5678", "예쁜네일", STARTS_AT)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.success("G-1"));

        cancelSmsService.send(RESERVATION_ID, CancelType.CUSTOMER_REQUEST);

        verify(solapiSmsClient).send("010-1234-5678",
                "[땡잡은 언니] 예쁜네일 11월 2일 10:00 고객 요청으로 예약이 취소되었습니다.");
        verify(notificationInboxService).save(
                USER_ID,
                NotificationInboxCategory.RESERVATION,
                "예약이 취소되었어요",
                "[땡잡은 언니] 예쁜네일 11월 2일 10:00 고객 요청으로 예약이 취소되었습니다.",
                NotificationInboxService.MY_RESERVATIONS_PATH);
    }

    @Test
    @DisplayName("고객 전화번호가 없으면 문자를 보내지 않는다")
    void send_noPhone_skips() {
        given(recipientContactRepository.findCancelSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo(USER_ID, null, "예쁜네일", STARTS_AT)));

        cancelSmsService.send(RESERVATION_ID, CancelType.CUSTOMER);

        verify(solapiSmsClient, never()).send(any(), any());
        verify(notificationInboxService, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("문자 발송이 실패하면 받은 알림을 남기지 않는다")
    void send_smsFailed_doesNotSaveInbox() {
        given(recipientContactRepository.findCancelSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo(USER_ID, "010-1234-5678", "예쁜네일", STARTS_AT)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.failed("발송 거절"));

        cancelSmsService.send(RESERVATION_ID, CancelType.CUSTOMER);

        verify(notificationInboxService, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("예약 정보가 없으면 문자를 보내지 않는다")
    void send_noReservation_skips() {
        given(recipientContactRepository.findCancelSmsInfo(RESERVATION_ID)).willReturn(Optional.empty());

        cancelSmsService.send(RESERVATION_ID, CancelType.SHOP_FAULT);

        verify(solapiSmsClient, never()).send(any(), any());
    }
}
