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
import com.ucamp.app.domain.notification.repository.RecipientContactRepository;
import com.ucamp.app.domain.notification.repository.RecipientContactRepository.CancelSmsInfo;

@ExtendWith(MockitoExtension.class)
class CancelSmsServiceTest {

    private static final Long RESERVATION_ID = 100L;
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private RecipientContactRepository recipientContactRepository;

    @Mock
    private SolapiSmsClient solapiSmsClient;

    private CancelSmsService cancelSmsService;

    @BeforeEach
    void setUp() {
        cancelSmsService = new CancelSmsService(recipientContactRepository, solapiSmsClient);
    }

    @Test
    @DisplayName("고객이 직접 취소하면 매장·예약 시각·환불 금액이 담긴 문자를 보낸다")
    void send_customerCancel_sendsCustomerMessage() {
        given(recipientContactRepository.findCancelSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo("010-1234-5678", "예쁜네일", STARTS_AT)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.success("G-1"));

        cancelSmsService.send(RESERVATION_ID, 20_000, CancelType.CUSTOMER);

        verify(solapiSmsClient).send("010-1234-5678",
                "[땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 취소되었습니다. 환불 금액: 20,000원");
    }

    @Test
    @DisplayName("사장님이 매장 사유로 취소하면 매장 사정 문구로 보낸다")
    void send_shopFault_sendsOwnerMessage() {
        given(recipientContactRepository.findCancelSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo("010-1234-5678", "예쁜네일", STARTS_AT)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.success("G-1"));

        cancelSmsService.send(RESERVATION_ID, 20_000, CancelType.SHOP_FAULT);

        verify(solapiSmsClient).send("010-1234-5678",
                "[땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 매장 사정으로 취소되었습니다. 환불 금액: 20,000원");
    }

    @Test
    @DisplayName("사장님이 고객 요청으로 대리 취소해도 매장 사정 문구로 보낸다")
    void send_customerRequest_sendsOwnerMessage() {
        given(recipientContactRepository.findCancelSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo("010-1234-5678", "예쁜네일", STARTS_AT)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.success("G-1"));

        cancelSmsService.send(RESERVATION_ID, 20_000, CancelType.CUSTOMER_REQUEST);

        verify(solapiSmsClient).send("010-1234-5678",
                "[땡잡은 언니] 예쁜네일 11월 2일 10:00 예약이 매장 사정으로 취소되었습니다. 환불 금액: 20,000원");
    }

    @Test
    @DisplayName("고객 전화번호가 없으면 문자를 보내지 않는다")
    void send_noPhone_skips() {
        given(recipientContactRepository.findCancelSmsInfo(RESERVATION_ID))
                .willReturn(Optional.of(new CancelSmsInfo(null, "예쁜네일", STARTS_AT)));

        cancelSmsService.send(RESERVATION_ID, 20_000, CancelType.CUSTOMER);

        verify(solapiSmsClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("예약 정보가 없으면 문자를 보내지 않는다")
    void send_noReservation_skips() {
        given(recipientContactRepository.findCancelSmsInfo(RESERVATION_ID)).willReturn(Optional.empty());

        cancelSmsService.send(RESERVATION_ID, 0, CancelType.SHOP_FAULT);

        verify(solapiSmsClient, never()).send(any(), any());
    }
}
