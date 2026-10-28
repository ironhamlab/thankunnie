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
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository.CancelSmsInfo;

@ExtendWith(MockitoExtension.class)
class ReservationSmsServiceTest {

    private static final Long RESERVATION_ID = 100L;
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private RecipientContactRepository recipientContactRepository;

    @Mock
    private SolapiSmsClient solapiSmsClient;

    private ReservationSmsService reservationSmsService;

    @BeforeEach
    void setUp() {
        reservationSmsService = new ReservationSmsService(recipientContactRepository, solapiSmsClient);
    }

    @Test
    @DisplayName("예약이 확정되면 고객 번호로 매장·예약 시각·결제 금액이 담긴 문자를 보낸다")
    void send_sendsConfirmedMessage() {
        given(recipientContactRepository.findReservationSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo("010-1234-5678", "예쁜네일", STARTS_AT)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.success("G-1"));

        reservationSmsService.send(RESERVATION_ID, 20_000);

        verify(solapiSmsClient).send("010-1234-5678",
                "[땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 확정되었습니다. 결제 금액: 20,000원");
    }

    @Test
    @DisplayName("고객 전화번호가 없으면 문자를 보내지 않는다")
    void send_noPhone_skips() {
        given(recipientContactRepository.findReservationSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo(null, "예쁜네일", STARTS_AT)));

        reservationSmsService.send(RESERVATION_ID, 20_000);

        verify(solapiSmsClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("예약 정보가 없으면 문자를 보내지 않는다")
    void send_noReservation_skips() {
        given(recipientContactRepository.findReservationSmsInfo(RESERVATION_ID)).willReturn(Optional.empty());

        reservationSmsService.send(RESERVATION_ID, 20_000);

        verify(solapiSmsClient, never()).send(any(), any());
    }
}
