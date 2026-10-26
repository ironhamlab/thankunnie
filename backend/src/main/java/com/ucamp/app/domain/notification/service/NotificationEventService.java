package com.ucamp.app.domain.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventType;
import com.ucamp.app.domain.notification.repository.NotificationEventRepository;
import com.ucamp.app.domain.notification.repository.VacancySlot;
import com.ucamp.app.domain.notification.repository.VacancySlotRepository;

/**
 * 빈자리 알림 이벤트 생성. 임박·초임박 진입은 스케줄러가 각각 주기적으로, 취소 재공개는 재공개 완료 시 만든다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationEventService {

    private final NotificationEventRepository notificationEventRepository;
    private final VacancySlotRepository vacancySlotRepository;

    /**
     * 임박 구간(시작 8시간 전부터 3시간 초과 전까지)에 들어온 빈자리마다 임박 이벤트를 한 번만 만든다.
     *
     * @param now 현재 시각 (서울)
     * @return 새로 만든 임박 이벤트 수
     */
    @Transactional
    public int createImminentEvents(LocalDateTime now) {
        // 조회가 이미 임박 이벤트가 없는 빈자리만 돌려준다. 예약 ID는 없다.
        List<VacancySlot> slots = vacancySlotRepository.findImminentWithoutEvent(now);

        for (VacancySlot slot : slots) {
            NotificationEvent event = NotificationEvent.builder()
                    .eventType(NotificationEventType.IMMINENT)
                    .exceptionId(slot.exceptionId())
                    .shopId(slot.shopId())
                    .slotStartsAt(slot.startsAt())
                    .slotEndsAt(slot.endsAt())
                    .build();
            notificationEventRepository.save(event);
        }
        return slots.size();
    }

    /**
     * 초임박 구간(시작 3시간 전)에 들어온 빈자리마다 초임박 이벤트를 한 번만 만든다.
     * 예약 ID는 없다. 같은 빈자리의 초임박 이벤트는 조회에서 이미 빠져 있다.
     *
     * @param now 현재 시각 (서울)
     * @return 새로 만든 초임박 이벤트 수
     */
    @Transactional
    public int createLastMinuteEvents(LocalDateTime now) {
        // 조회가 이미 초임박 이벤트가 없는 빈자리만 돌려준다. 예약 ID는 없다.
        List<VacancySlot> slots = vacancySlotRepository.findLastMinuteWithoutEvent(now);

        for (VacancySlot slot : slots) {
            NotificationEvent event = NotificationEvent.builder()
                    .eventType(NotificationEventType.LAST_MINUTE)
                    .exceptionId(slot.exceptionId())
                    .shopId(slot.shopId())
                    .slotStartsAt(slot.startsAt())
                    .slotEndsAt(slot.endsAt())
                    .build();
            notificationEventRepository.save(event);
        }
        return slots.size();
    }

    /**
     * 취소된 예약 시간이 실제로 다시 판매 가능할 때만 재공개 이벤트를 만든다.
     * 예약이 취소됐다는 사실만으로는 만들지 않는다. 재공개 커밋 뒤에 호출되므로 새 트랜잭션에서 저장한다.
     *
     * @param reservationId 취소된 예약 ID
     * @param now           현재 시각 (서울)
     * @return 이벤트를 만들었으면 true, 판매 가능하지 않거나 이미 있으면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createReopenedEvent(Long reservationId, LocalDateTime now) {
        Optional<VacancySlot> reopened = vacancySlotRepository.findReopened(reservationId, now);
        if (reopened.isEmpty()) {
            log.info("재공개 이벤트 생략: 판매 가능한 빈자리가 아님 reservationId={}", reservationId);
            return false;
        }

        VacancySlot slot = reopened.get();
        // 같은 빈자리, 같은 시작 시각의 재공개 이벤트는 한 번만 만든다.
        boolean alreadyCreated = notificationEventRepository.existsByExceptionIdAndEventTypeAndSlotStartsAt(
                slot.exceptionId(), NotificationEventType.REOPENED, slot.startsAt());

        if (alreadyCreated) {
            return false;
        }
        
        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.REOPENED)
                .exceptionId(slot.exceptionId())
                .shopId(slot.shopId())
                .reservationId(slot.reservationId())
                .slotStartsAt(slot.startsAt())
                .slotEndsAt(slot.endsAt())
                .build();
        notificationEventRepository.save(event);
        return true;
    }
}
