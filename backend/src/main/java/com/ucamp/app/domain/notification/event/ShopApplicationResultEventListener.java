package com.ucamp.app.domain.notification.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.notification.service.ShopApplicationSmsService;

/**
 * 가입 승인·거절을 받아 매장 전화번호로 결과 문자를 보낸다.
 * 승인·거절 트랜잭션이 커밋된 뒤에 실행하므로, 문자 발송이 실패해도 승인·거절은 되돌리지 않는다.
 * 트랜잭션이 없으면 실행하지 않는다. 롤백된 승인·거절에는 문자를 보내지 않기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopApplicationResultEventListener {

    private final ShopApplicationSmsService shopApplicationSmsService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleApproved(ShopApplicationApprovedEvent event) {
        try {
            shopApplicationSmsService.sendApproved(event.shopId());
        } catch (RuntimeException e) {
            // 이미 커밋된 승인 응답이 문자 오류로 실패하지 않게 기록만 한다
            log.error("가입 승인 문자 발송 중 오류 shopId={}", event.shopId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRejected(ShopApplicationRejectedEvent event) {
        try {
            shopApplicationSmsService.sendRejected(event.shopId(), event.rejectReason());
        } catch (RuntimeException e) {
            // 이미 커밋된 거절 응답이 문자 오류로 실패하지 않게 기록만 한다
            log.error("가입 거절 문자 발송 중 오류 shopId={}", event.shopId(), e);
        }
    }
}
