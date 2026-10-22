package com.ucamp.app.domain.notification.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionCreateRequest;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionUpdateRequest;
import com.ucamp.app.domain.notification.dto.response.NotificationSubscriptionResponse;
import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionStatus;
import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;

/**
 * 알림 구독 등록·조회·수정·해제와 알림 대상 구독 조회.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final NotificationSubscriptionRepository notificationSubscriptionRepository;

    @Transactional
    public NotificationSubscriptionResponse subscribe(AuthUser loginUser,
            NotificationSubscriptionCreateRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        validateNotExpired(request.localEndTime());
        NotificationSubscription subscription = NotificationSubscription.builder()
                .userId(loginUser.userId())
                .subscriptionType(request.subscriptionType())
                .shopId(request.shopId())
                .categoryCode(request.categoryCode())
                .regionCode(request.regionCode())
                .startTime(request.localStartTime())
                .endTime(request.localEndTime())
                .build();
        return NotificationSubscriptionResponse.from(notificationSubscriptionRepository.save(subscription));
    }

    /**
     * 본인의 구독 중 해제하지 않은 구독을 최신 등록 순으로 조회한다.
     */
    public List<NotificationSubscriptionResponse> getMySubscriptions(AuthUser loginUser) {
        loginUser.requireRole(UserRole.CUSTOMER);
        return notificationSubscriptionRepository
                .findAllByUserIdAndStatusOrderByIdDesc(loginUser.userId(), SubscriptionStatus.ACTIVE)
                .stream()
                .map(NotificationSubscriptionResponse::from)
                .toList();
    }

    @Transactional
    public NotificationSubscriptionResponse updateSubscription(AuthUser loginUser, Long subscriptionId,
            NotificationSubscriptionUpdateRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        NotificationSubscription subscription = getActiveSubscription(loginUser, subscriptionId);
        validateNotExpired(request.localEndTime());
        subscription.changeCondition(request.shopId(), request.categoryCode(), request.regionCode(),
                request.localStartTime(), request.localEndTime());
        return NotificationSubscriptionResponse.from(subscription);
    }

    @Transactional
    public void unsubscribe(AuthUser loginUser, Long subscriptionId) {
        loginUser.requireRole(UserRole.CUSTOMER);
        getActiveSubscription(loginUser, subscriptionId).deactivate();
    }

    /**
     * 매장 빈자리의 알림 대상 구독. 해제·만료 구독은 제외한다.
     */
    public List<NotificationSubscription> findNotifiableByShop(Long shopId, LocalDateTime slotStartTime) {
        return notificationSubscriptionRepository.findNotifiableByShop(shopId, slotStartTime, now());
    }

    /**
     * 카테고리·지역 빈자리의 알림 대상 구독. 해제·만료 구독은 제외한다.
     */
    public List<NotificationSubscription> findNotifiableByCategoryRegion(String categoryCode, String regionCode,
            LocalDateTime slotStartTime) {
        return notificationSubscriptionRepository.findNotifiableByCategoryRegion(
                categoryCode, regionCode, slotStartTime, now());
    }

    // 다른 고객의 구독과 이미 해제한 구독은 존재 여부를 드러내지 않도록 모두 NOT_FOUND 로 처리한다
    private NotificationSubscription getActiveSubscription(AuthUser loginUser, Long subscriptionId) {
        return notificationSubscriptionRepository.findByIdAndUserId(subscriptionId, loginUser.userId())
                .filter(NotificationSubscription::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    private void validateNotExpired(LocalDateTime endTime) {
        if (!endTime.isAfter(now())) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_INVALID_TIME);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZONE_SEOUL);
    }
}
