package com.ucamp.app.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;
import com.ucamp.app.domain.notification.repository.ShopCategoryRegion;
import com.ucamp.app.domain.notification.repository.ShopCategoryRegionRepository;

/**
 * 이 빈자리를 누구에게 알려 줄지 고르는 곳.
 * 그 매장을 신청한 사람과, 그 매장의 카테고리·지역을 신청한 사람을 찾는다.
 * 매장 구독으로 이 빈자리 알림을 받을 회원은 카테고리 목록에서 뺀다.
 * 두 쪽에 모두 있어도 회원은 한 명만 남긴다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SubscriptionMatchService {

    private final NotificationSubscriptionRepository notificationSubscriptionRepository;
    private final ShopCategoryRegionRepository shopCategoryRegionRepository;
    private final Clock clock;

    /**
     * 조건 A. 이 매장 빈자리를 받을 업장 구독을 고른다.
     * 구독 매장 ID가 빈자리 매장 ID와 같고, 빈자리 시작 시각이 희망 시간 안이어야 한다.
     * 해제한 구독과 희망 종료가 지난 구독은 빠진다.
     *
     * @param shopId       빈자리 매장 번호
     * @param slotStartsAt 빈자리 시작 시각
     * @return 조건 A에 맞는 업장 구독. 없거나 시각이 없으면 빈 목록
     */
    public List<NotificationSubscription> findStoreMatches(Long shopId, LocalDateTime slotStartsAt) {
        if (shopId == null || slotStartsAt == null) {
            return List.of();
        }
        return notificationSubscriptionRepository.findNotifiableByShop(shopId, slotStartsAt, now());
    }

    /**
     * 조건 B. 이 매장 빈자리를 받을 카테고리·지역 구독을 고른다.
     * 구독 카테고리와 지역코드가 매장 값과 같고, 빈자리 시작 시각이 희망 시간 안이어야 한다.
     * 해제한 구독과 희망 종료가 지난 구독은 빠진다.
     *
     * @param shopId       빈자리 매장 번호
     * @param slotStartsAt 빈자리 시작 시각
     * @return 조건 B에 맞는 카테고리·지역 구독. 매장이나 시각이 없으면 빈 목록
     */
    public List<NotificationSubscription> findCategoryRegionMatches(Long shopId, LocalDateTime slotStartsAt) {
        if (shopId == null || slotStartsAt == null) {
            return List.of();
        }

        // 이벤트에는 매장 ID만 있다. 카테고리와 지역코드는 매장 행에서 읽는다.
        Optional<ShopCategoryRegion> shop = shopCategoryRegionRepository.findByShopId(shopId);
        if (shop.isEmpty()) {
            return List.of();
        }

        ShopCategoryRegion categoryRegion = shop.get();
        return notificationSubscriptionRepository.findNotifiableByCategoryRegion(
                categoryRegion.categoryId(), categoryRegion.regionCode(), slotStartsAt, now());
    }

    /**
     * 이 빈자리의 발송 대상을 매장 구독과 카테고리 구독으로 나눈다.
     * 매장 구독으로 이 빈자리 알림을 받을 회원은 카테고리 목록에 넣지 않는다.
     * 그 회원의 카테고리 구독이 여러 개여도 전부 뺀다.
     * 카테고리 구독으로만 받을 회원은 구독을 그대로 둔다. 구독이 두 개면 두 개 다 남긴다.
     *
     * @param shopId       빈자리 매장 번호
     * @param slotStartsAt 빈자리 시작 시각
     * @return 매장 구독으로 받을 회원과, 그 회원을 뺀 카테고리·지역 구독. 없으면 양쪽 다 빈 목록
     */
    public SubscriptionMatchGroups findMatchGroups(Long shopId, LocalDateTime slotStartsAt) {
        List<Long> storeUserIds = new ArrayList<>();
        addUserIds(storeUserIds, findStoreMatches(shopId, slotStartsAt));

        List<NotificationSubscription> categoryOnly = new ArrayList<>();
        for (NotificationSubscription match : findCategoryRegionMatches(shopId, slotStartsAt)) {
            if (storeUserIds.contains(match.getUserId())) {
                continue;
            }
            categoryOnly.add(match);
        }
        return new SubscriptionMatchGroups(List.copyOf(storeUserIds), List.copyOf(categoryOnly));
    }

    /**
     * 조건 A와 조건 B로 이 빈자리 알림을 받을 회원을 고른다.
     * 둘 중 하나로라도 받을 수 있으면 포함한다. 둘 다로 받을 수 있어도 회원은 한 번만 넣는다.
     *
     * @param shopId       빈자리 매장 번호
     * @param slotStartsAt 빈자리 시작 시각
     * @return 발송 대상 회원 ID. 업장 구독 회원이 먼저이고, 이어서 카테고리 구독으로만 받을 회원이다
     */
    public List<Long> findRecipientUserIds(Long shopId, LocalDateTime slotStartsAt) {
        SubscriptionMatchGroups groups = findMatchGroups(shopId, slotStartsAt);
        List<Long> userIds = new ArrayList<>(groups.storeUserIds());
        addUserIds(userIds, groups.categoryRegionOnlySubscriptions());
        return userIds;
    }

    // 이미 목록에 있는 회원은 다시 넣지 않는다.
    private void addUserIds(List<Long> userIds, List<NotificationSubscription> matches) {
        for (NotificationSubscription match : matches) {
            Long userId = match.getUserId();
            if (userIds.contains(userId)) {
                continue;
            }
            userIds.add(userId);
        }
    }

    // 테스트에서 시각을 고정할 수 있게, 주입받은 Clock으로 현재 시각을 구한다.
    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
