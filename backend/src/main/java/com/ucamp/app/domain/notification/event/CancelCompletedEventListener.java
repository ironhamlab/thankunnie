package com.ucamp.app.domain.notification.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.notification.service.CancelSmsService;

/**
 * 취소 완료를 받아 고객에게 취소 안내 문자를 보낸다.
 * 취소 트랜잭션이 커밋된 뒤에 실행하므로, 문자가 실패해도 취소는 되돌리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelCompletedEventListener {

    private final CancelSmsService cancelSmsService;

    @TransactionalEventListener
    public void handle(CancelCompletedEvent event) {
        try {
            cancelSmsService.send(event.reservationId(), event.refundAmount(), event.cancelType());
        } catch (RuntimeException e) {
            // 이미 커밋된 취소 응답이 문자 오류로 실패하지 않게 기록만 한다
            log.error("취소 문자 발송 중 오류 reservationId={}", event.reservationId(), e);
        }
    }
}
