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

import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository.CancelSmsInfo;

@ExtendWith(MockitoExtension.class)
class ReservationSmsServiceTest {

    private static final Long RESERVATION_ID = 100L;
    private static final Long USER_ID = 7L;
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final String CONFIRMED_MESSAGE = "[땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 확정되었습니다.";

    @Mock
    private RecipientContactRepository recipientContactRepository;

    @Mock
    private SolapiSmsClient solapiSmsClient;

    @Mock
    private NotificationInboxService notificationInboxService;

    private ReservationSmsService reservationSmsService;

    @BeforeEach
    void setUp() {
        reservationSmsService = new ReservationSmsService(
                recipientContactRepository, solapiSmsClient, notificationInboxService);
    }

    @Test
    @DisplayName("예약이 확정되면 고객 번호로 매장·예약 시각이 담긴 문자를 보낸다")
    void send_sendsConfirmedMessage() {
        given(recipientContactRepository.findReservationSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo(USER_ID, "010-1234-5678", "예쁜네일", STARTS_AT)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.success("G-1"));

        reservationSmsService.send(RESERVATION_ID);

        verify(solapiSmsClient).send("010-1234-5678", CONFIRMED_MESSAGE);
        verify(notificationInboxService).save(
                USER_ID,
                NotificationInboxCategory.RESERVATION,
                "예약이 확정되었어요",
                CONFIRMED_MESSAGE,
                NotificationInboxService.MY_RESERVATIONS_PATH);
    }

    @Test
    @DisplayName("문자 발송이 실패하면 받은 알림을 남기지 않는다")
    void send_smsFailed_doesNotSaveInbox() {
        given(recipientContactRepository.findReservationSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo(USER_ID, "010-1234-5678", "예쁜네일", STARTS_AT)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.failed("발송 거절"));

        reservationSmsService.send(RESERVATION_ID);

        verify(notificationInboxService, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("고객 전화번호가 없으면 문자를 보내지 않는다")
    void send_noPhone_skips() {
        given(recipientContactRepository.findReservationSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo(USER_ID, null, "예쁜네일", STARTS_AT)));

        reservationSmsService.send(RESERVATION_ID);

        verify(solapiSmsClient, never()).send(any(), any());
        verify(notificationInboxService, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("예약 정보가 없으면 문자를 보내지 않는다")
    void send_noReservation_skips() {
        given(recipientContactRepository.findReservationSmsInfo(RESERVATION_ID)).willReturn(Optional.empty());

        reservationSmsService.send(RESERVATION_ID);

        verify(solapiSmsClient, never()).send(any(), any());
    }
}
