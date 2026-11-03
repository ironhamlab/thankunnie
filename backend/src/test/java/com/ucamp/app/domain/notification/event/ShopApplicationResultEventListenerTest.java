package com.ucamp.app.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ucamp.app.domain.notification.service.ShopApplicationSmsService;

@ExtendWith(MockitoExtension.class)
class ShopApplicationResultEventListenerTest {

    private static final Long SHOP_ID = 10L;

    @Mock
    private ShopApplicationSmsService shopApplicationSmsService;

    private ShopApplicationResultEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ShopApplicationResultEventListener(shopApplicationSmsService);
    }

    @Test
    @DisplayName("승인 이벤트를 받으면 승인 문자를 보낸다")
    void handleApproved_sendsApprovedSms() {
        listener.handleApproved(new ShopApplicationApprovedEvent(SHOP_ID));

        verify(shopApplicationSmsService).sendApproved(SHOP_ID);
    }

    @Test
    @DisplayName("거절 이벤트를 받으면 거절 사유를 담아 거절 문자를 보낸다")
    void handleRejected_sendsRejectedSms() {
        listener.handleRejected(new ShopApplicationRejectedEvent(SHOP_ID, "서비스 대상 업종이 아니에요."));

        verify(shopApplicationSmsService).sendRejected(SHOP_ID, "서비스 대상 업종이 아니에요.");
    }

    @Test
    @DisplayName("승인 문자 발송이 실패해도 예외를 밖으로 던지지 않는다")
    void handleApproved_smsFails_doesNotThrow() {
        doThrow(new RuntimeException("문자 실패")).when(shopApplicationSmsService).sendApproved(SHOP_ID);

        assertThatCode(() -> listener.handleApproved(new ShopApplicationApprovedEvent(SHOP_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("거절 문자 발송이 실패해도 예외를 밖으로 던지지 않는다")
    void handleRejected_smsFails_doesNotThrow() {
        doThrow(new RuntimeException("문자 실패"))
                .when(shopApplicationSmsService).sendRejected(SHOP_ID, "사유");

        assertThatCode(() -> listener.handleRejected(new ShopApplicationRejectedEvent(SHOP_ID, "사유")))
                .doesNotThrowAnyException();
    }
}
