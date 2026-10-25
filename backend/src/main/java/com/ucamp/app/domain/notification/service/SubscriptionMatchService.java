package com.ucamp.app.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;

/**
 * 빈자리에 맞는 구독을 고른다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SubscriptionMatchService {

    private final NotificationSubscriptionRepository notificationSubscriptionRepository;
    private final Clock clock;

    /**
     * 조건 A. 이 매장 빈자리를 받을 업장 구독을 고른다.
     * 구독 매장 ID가 빈자리 매장 ID와 같고, 빈자리 시작 시각이 희망 시간 안이어야 한다.
     * 해제한 구독과 희망 종료가 지난 구독은 빠진다.
     *
     * @param shopId       빈자리 매장 ID
     * @param slotStartsAt 빈자리 시작 시각
     * @return 조건 A에 맞는 업장 구독. 없거나 시각이 없으면 빈 목록
     */
    public List<NotificationSubscription> findStoreMatches(Long shopId, LocalDateTime slotStartsAt) {
        if (shopId == null || slotStartsAt == null) {
            return List.of();
        }
        return notificationSubscriptionRepository.findNotifiableByShop(shopId, slotStartsAt, now());
    }

    // 테스트에서 시각을 고정할 수 있게, 주입받은 Clock으로 현재 시각을 구한다.
    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
