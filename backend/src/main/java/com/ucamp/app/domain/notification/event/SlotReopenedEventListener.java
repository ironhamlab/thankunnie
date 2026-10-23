package com.ucamp.app.domain.notification.event;

import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.domain.notification.service.NotificationEventService;

/**
 * 재공개 완료를 받아 알림 이벤트를 만든다.
 * 재공개 트랜잭션이 커밋된 뒤에 실행하므로, 알림 이벤트 저장이 실패해도 취소·재공개는 되돌리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotReopenedEventListener {

    private final NotificationEventService notificationEventService;

    /**
     * 커밋된 재공개 상태를 다시 확인해 재공개 이벤트를 만든다.
     *
     * @param event 재공개 완료 이벤트
     */
    @TransactionalEventListener(fallbackExecution = true)
    public void handle(SlotReopenedEvent event) {
        try {
            notificationEventService.createReopenedEvent(event.reservationId(), LocalDateTime.now(BookingPolicy.ZONE));
        } catch (DataIntegrityViolationException e) {
            // 같은 빈자리에 동시에 들어온 요청은 UNIQUE 제약으로 한 건만 남는다
            log.info("재공개 이벤트 중복 생략 reservationId={}", event.reservationId());
        } catch (RuntimeException e) {
            // 이미 커밋된 취소·재공개 응답이 알림 실패로 오류가 되지 않게 기록만 한다
            log.error("재공개 이벤트 생성 실패 reservationId={}", event.reservationId(), e);
        }
    }
}
