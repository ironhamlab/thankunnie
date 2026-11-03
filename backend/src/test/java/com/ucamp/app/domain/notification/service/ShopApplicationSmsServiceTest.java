package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ucamp.app.domain.auth.entity.ShopAccount;
import com.ucamp.app.domain.auth.repository.ShopAccountRepository;
import com.ucamp.app.domain.notification.client.SmsSendResult;
import com.ucamp.app.domain.notification.client.SolapiSmsClient;

@ExtendWith(MockitoExtension.class)
class ShopApplicationSmsServiceTest {

    private static final Long SHOP_ID = 10L;
    private static final String PHONE = "010-1234-5678";
    private static final String APPROVED_MESSAGE = "[땡잡은 언니] 예쁜네일 가입이 승인되었습니다.";
    private static final String REJECTED_MESSAGE =
            "[땡잡은 언니] 예쁜네일 가입이 거절되었습니다. 사유: 서비스 대상 업종이 아니에요.";

    @Mock
    private ShopAccountRepository shopAccountRepository;

    @Mock
    private SolapiSmsClient solapiSmsClient;

    private ShopApplicationSmsService shopApplicationSmsService;

    @BeforeEach
    void setUp() {
        shopApplicationSmsService = new ShopApplicationSmsService(shopAccountRepository, solapiSmsClient);
    }

    @Test
    @DisplayName("승인하면 매장 전화번호로 승인 문자를 보낸다")
    void sendApproved_sendsToShopPhone() {
        given(shopAccountRepository.findById(SHOP_ID)).willReturn(Optional.of(shop(PHONE)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.success("G-1"));

        shopApplicationSmsService.sendApproved(SHOP_ID);

        verify(solapiSmsClient).send(PHONE, APPROVED_MESSAGE);
    }

    @Test
    @DisplayName("거절하면 매장 전화번호로 거절 사유가 담긴 문자를 보낸다")
    void sendRejected_sendsReasonToShopPhone() {
        given(shopAccountRepository.findById(SHOP_ID)).willReturn(Optional.of(shop(PHONE)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.success("G-1"));

        shopApplicationSmsService.sendRejected(SHOP_ID, "서비스 대상 업종이 아니에요.");

        verify(solapiSmsClient).send(PHONE, REJECTED_MESSAGE);
    }

    @Test
    @DisplayName("매장 전화번호가 없으면 문자를 보내지 않는다")
    void sendApproved_noPhone_skips() {
        given(shopAccountRepository.findById(SHOP_ID)).willReturn(Optional.of(shop("   ")));

        shopApplicationSmsService.sendApproved(SHOP_ID);

        verify(solapiSmsClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("거절이어도 매장 전화번호가 없으면 문자를 보내지 않는다")
    void sendRejected_noPhone_skips() {
        given(shopAccountRepository.findById(SHOP_ID)).willReturn(Optional.of(shop(null)));

        shopApplicationSmsService.sendRejected(SHOP_ID, "사유");

        verify(solapiSmsClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("매장명이나 거절 사유가 없어도 문자에 null 이라는 글자를 넣지 않는다")
    void sendRejected_nullNameAndReason_sendsEmptyText() {
        given(shopAccountRepository.findById(SHOP_ID)).willReturn(Optional.of(shop(PHONE, null)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.success("G-1"));

        shopApplicationSmsService.sendRejected(SHOP_ID, null);

        verify(solapiSmsClient).send(PHONE, "[땡잡은 언니]  가입이 거절되었습니다. 사유: ");
    }

    @Test
    @DisplayName("매장이 없으면 문자를 보내지 않는다")
    void sendApproved_noShop_skips() {
        given(shopAccountRepository.findById(SHOP_ID)).willReturn(Optional.empty());

        shopApplicationSmsService.sendApproved(SHOP_ID);

        verify(solapiSmsClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("문자 발송이 실패해도 예외를 던지지 않는다")
    void sendApproved_smsFailed_doesNotThrow() {
        given(shopAccountRepository.findById(SHOP_ID)).willReturn(Optional.of(shop(PHONE)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.failed("발송 거절"));

        assertThatCode(() -> shopApplicationSmsService.sendApproved(SHOP_ID)).doesNotThrowAnyException();
        verify(solapiSmsClient).send(PHONE, APPROVED_MESSAGE);
    }

    @Test
    @DisplayName("문자 결과가 불명이어도 예외를 던지지 않는다")
    void sendRejected_smsUnknown_doesNotThrow() {
        given(shopAccountRepository.findById(SHOP_ID)).willReturn(Optional.of(shop(PHONE)));
        given(solapiSmsClient.send(any(), any())).willReturn(SmsSendResult.unknown("응답 없음"));

        assertThatCode(() -> shopApplicationSmsService.sendRejected(SHOP_ID, "사유"))
                .doesNotThrowAnyException();
    }

    private static ShopAccount shop(String phone) {
        return shop(phone, "예쁜네일");
    }

    private static ShopAccount shop(String phone, String name) {
        return ShopAccount.builder()
                .name(name)
                .phone(phone)
                .build();
    }
}
