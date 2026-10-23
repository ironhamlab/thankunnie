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
 * 알림 구독 업무.
 * 고객의 구독 등록·목록·수정·해제와, 해제·만료를 뺀 알림 대상 조회를 담당한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final NotificationSubscriptionRepository notificationSubscriptionRepository;

    /**
     * 업장 구독 또는 카테고리·지역 구독을 등록한다.
     *
     * @param loginUser 로그인한 고객
     * @param request   구독 등록 요청
     * @return 등록된 구독
     */
    @Transactional
    public NotificationSubscriptionResponse subscribe(AuthUser loginUser,
            NotificationSubscriptionCreateRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        validateNotExpired(request.localEndTime());
        NotificationSubscription subscription = NotificationSubscription.builder()
                .userId(loginUser.userId())
                .subscriptionType(request.subscriptionType())
                .shopId(request.shopId())
                .categoryName(request.categoryName())
                .regionName(request.regionName())
                .startTime(request.localStartTime())
                .endTime(request.localEndTime())
                .build();
        return NotificationSubscriptionResponse.from(notificationSubscriptionRepository.save(subscription));
    }

    /**
     * 본인의 구독 중 해제하지 않은 구독을 최신 등록 순으로 조회한다.
     *
     * @param loginUser 로그인한 고객
     * @return 활성 구독 목록
     */
    public List<NotificationSubscriptionResponse> getMySubscriptions(AuthUser loginUser) {
        loginUser.requireRole(UserRole.CUSTOMER);
        return notificationSubscriptionRepository
                .findAllByUserIdAndStatusOrderByIdDesc(loginUser.userId(), SubscriptionStatus.ACTIVE)
                .stream()
                .map(NotificationSubscriptionResponse::from)
                .toList();
    }

    /**
     * 본인의 활성 구독에서 대상과 희망 시간을 바꾼다. 구독 유형은 유지한다.
     *
     * @param loginUser      로그인한 고객
     * @param subscriptionId 구독 ID
     * @param request        구독 수정 요청
     * @return 수정된 구독
     */
    @Transactional
    public NotificationSubscriptionResponse updateSubscription(AuthUser loginUser, Long subscriptionId,
            NotificationSubscriptionUpdateRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        NotificationSubscription subscription = getActiveSubscription(loginUser, subscriptionId);
        validateNotExpired(request.localEndTime());
        subscription.changeCondition(request.shopId(), request.categoryName(), request.regionName(),
                request.localStartTime(), request.localEndTime());
        return NotificationSubscriptionResponse.from(subscription);
    }

    /**
     * 본인의 구독을 해제한다. 이후 목록과 알림 대상에서 빠진다.
     *
     * @param loginUser      로그인한 고객
     * @param subscriptionId 구독 ID
     */
    @Transactional
    public void unsubscribe(AuthUser loginUser, Long subscriptionId) {
        loginUser.requireRole(UserRole.CUSTOMER);
        getActiveSubscription(loginUser, subscriptionId).deactivate();
    }

    /**
     * 매장 빈자리의 알림 대상 구독을 조회한다. 해제·만료 구독은 제외한다.
     *
     * @param shopId        매장 ID
     * @param slotStartTime 빈자리 시작 시각
     * @return 알림 대상 구독 목록
     */
    public List<NotificationSubscription> findNotifiableByShop(Long shopId, LocalDateTime slotStartTime) {
        return notificationSubscriptionRepository.findNotifiableByShop(shopId, slotStartTime, now());
    }

    /**
     * 카테고리·지역 빈자리의 알림 대상 구독을 조회한다. 해제·만료 구독은 제외한다.
     *
     * @param categoryName  카테고리명
     * @param regionName    지역명
     * @param slotStartTime 빈자리 시작 시각
     * @return 알림 대상 구독 목록
     */
    public List<NotificationSubscription> findNotifiableByCategoryRegion(String categoryName, String regionName,
            LocalDateTime slotStartTime) {
        return notificationSubscriptionRepository.findNotifiableByCategoryRegion(
                categoryName, regionName, slotStartTime, now());
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
