package com.ucamp.app.domain.notification.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

/**
 * 빈자리 알림 소식을 만드는 곳.
 * 임박·초임박은 1분마다 찾고, 재공개는 취소가 끝난 뒤에 만든다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationEventService {

    private final NotificationEventRepository notificationEventRepository;
    private final VacancySlotRepository vacancySlotRepository;
    private final AvailabilityService availabilityService;

    /**
     * 임박 구간(시작 8시간 전부터 3시간 초과 전까지)에 들어온 빈자리마다 임박 이벤트를 한 번만 만든다.
     *
     * @param now 현재 시각 (서울)
     * @return 새로 만든 임박 이벤트 수
     */
    @Transactional
    public int createImminentEvents(LocalDateTime now) {
        // 조회가 이미 임박 이벤트가 없는 빈자리만 돌려준다. 예약 ID는 없다.
        // 같은 매장·시작 시각 OPEN이 여러 개면 UK에 맞춰 하나만 남긴다.
        List<VacancySlot> slots = distinctByShopAndStartsAt(
                vacancySlotRepository.findImminentWithoutEvent(now));

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
        // 같은 매장·시작 시각 OPEN이 여러 개면 UK에 맞춰 하나만 남긴다.
        List<VacancySlot> slots = distinctByShopAndStartsAt(
                vacancySlotRepository.findLastMinuteWithoutEvent(now));

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
     * 같은 매장·같은 시작 시각은 이벤트 UK가 하나라서, 조회에 여러 OPEN이 와도 첫 행만 남긴다.
     * 조회가 EXCEPTION_ID 오름차순이라 보통 번호가 작은 OPEN이 남는다.
     */
    private static List<VacancySlot> distinctByShopAndStartsAt(List<VacancySlot> slots) {
        Map<ShopStartsAt, VacancySlot> unique = new LinkedHashMap<>();
        for (VacancySlot slot : slots) {
            unique.putIfAbsent(new ShopStartsAt(slot.shopId(), slot.startsAt()), slot);
        }
        return new ArrayList<>(unique.values());
    }

    private record ShopStartsAt(Long shopId, LocalDateTime startsAt) {
    }

    /**
     * 취소된 예약 시간이 지금 다시 예약 가능할 때만 재공개 소식을 만든다.
     * 취소됐다는 것만으로는 만들지 않는다.
     * OPEN 행이 없어도, 영업시간 기준으로 팔리면 만든다.
     *
     * @param reservationId 취소된 예약 번호
     * @param now           현재 시각 (서울). 호출 쪽에서 넘기지만, 가능 여부는 AvailabilityService 시계 기준이다
     * @return 소식을 만들었으면 true. 아직 못 팔거나 이 예약의 재공개가 이미 있으면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createReopenedEvent(Long reservationId, LocalDateTime now) {
        Optional<VacancySlot> cancelled = vacancySlotRepository.findCancelledReservation(reservationId);
        if (cancelled.isEmpty()) {
            log.info("재공개 이벤트 생략: 취소 완료된 일반 시술 예약이 아님 reservationId={}", reservationId);
            return false;
        }

        VacancySlot slot = cancelled.get();
        int durationMinutes = (int) Duration.between(slot.startsAt(), slot.endsAt()).toMinutes();
        if (durationMinutes <= 0) {
            log.info("재공개 이벤트 생략: 예약 시간이 올바르지 않음 reservationId={}", reservationId);
            return false;
        }

        // 화면과 같은 기준으로, 지금 이 시각에 예약할 수 있는지 본다.
        if (!availabilityService.isAvailable(slot.shopId(), slot.startsAt(), durationMinutes)) {
            log.info("재공개 이벤트 생략: 아직 예약할 수 없음 reservationId={}", reservationId);
            return false;
        }

        // 같은 예약의 재공개만 한 번으로 막는다. 다른 예약이 같은 시각에 나중에 취소되면 새로 만든다.
        boolean alreadyCreated = notificationEventRepository.existsByEventTypeAndReservationId(
                NotificationEventType.REOPENED, slot.reservationId());
        if (alreadyCreated) {
            return false;
        }

        NotificationEvent event = NotificationEvent.builder()
                .eventType(NotificationEventType.REOPENED)
                .exceptionId(null)
                .shopId(slot.shopId())
                .reservationId(slot.reservationId())
                .slotStartsAt(slot.startsAt())
                .slotEndsAt(slot.endsAt())
                .build();
        notificationEventRepository.save(event);
        return true;
    }
}
