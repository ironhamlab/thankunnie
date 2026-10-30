package com.ucamp.app.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
import com.ucamp.app.domain.notification.entity.SubscriptionType;
import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;
import com.ucamp.app.domain.user.entity.TermsCode;
import com.ucamp.app.domain.user.service.TermsAgreementService;

/**
 * 알림 구독 업무.
 * 고객의 구독 등록·목록·수정·해제와, 해제·만료를 뺀 알림 대상 조회를 담당한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationSubscriptionRepository notificationSubscriptionRepository;
    private final Clock clock;
    private final TermsAgreementService termsAgreementService;

    /**
     * 업장 구독 또는 카테고리·지역 구독을 등록한다.
     * 희망 시간이 있는(문자가 나가는) 구독은 알림 문자 수신 동의(SMS_NOTIFICATION)가 없으면
     * TERMS_SMS_AGREEMENT_REQUIRED 를 던진다. 희망 시간 없는 업장 찜은 문자가 나가지 않아 검사하지 않는다.
     * 프론트는 이 에러를 받으면 동의 모달을 띄운 뒤 다시 요청한다.
     *
     * @param loginUser 로그인한 고객
     * @param request   구독 등록 요청
     * @return 등록된 구독
     */
    @Transactional
    public NotificationSubscriptionResponse subscribe(AuthUser loginUser,
            NotificationSubscriptionCreateRequest request) {
        loginUser.requireRole(UserRole.CUSTOMER);
        requireSmsAgreementIfNotifying(loginUser, request.startTime(), request.endTime());
        validateNotExpired(request.localEndTime());
        // 대상·시간 형식을 먼저 검사한 뒤 중복을 본다 (잘못된 요청이 409 로 나가면 안 됨)
        NotificationSubscription subscription = NotificationSubscription.builder()
                .userId(loginUser.userId())
                .subscriptionType(request.subscriptionType())
                .shopId(request.shopId())
                .categoryId(request.categoryId())
                .regionCode(request.regionCode())
                .startTime(request.localStartTime())
                .endTime(request.localEndTime())
                .build();
        // 등록이므로 제외할 구독 ID 는 없다 (excludeId = null)
        validateNoDuplicate(loginUser.userId(), subscription.getSubscriptionType(), subscription.getShopId(),
                subscription.getCategoryId(), subscription.getRegionCode(), subscription.getStartTime(),
                subscription.getEndTime(), null);
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
     * 찜(희망 시간 없음)에 희망 시간을 넣어 알림을 켜는 경우를 포함해, 희망 시간이 있으면 문자 수신 동의를 검사한다.
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
        requireSmsAgreementIfNotifying(loginUser, request.startTime(), request.endTime());
        validateNotExpired(request.localEndTime());
        // 대상·시간 형식을 먼저 검사한 뒤 중복을 본다 (잘못된 요청이 409 로 나가면 안 됨)
        subscription.changeCondition(request.shopId(), request.categoryId(), request.regionCode(),
                request.localStartTime(), request.localEndTime());
        // 수정이므로 자기 구독은 중복에서 뺀다 (excludeId = 자기 ID). 중복이면 트랜잭션이 롤백된다.
        validateNoDuplicate(loginUser.userId(), subscription.getSubscriptionType(), subscription.getShopId(),
                subscription.getCategoryId(), subscription.getRegionCode(), subscription.getStartTime(),
                subscription.getEndTime(), subscription.getId());
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
     * @param categoryId    카테고리 ID
     * @param regionCode    지역코드
     * @param slotStartTime 빈자리 시작 시각
     * @return 알림 대상 구독 목록
     */
    public List<NotificationSubscription> findNotifiableByCategoryRegion(Long categoryId, String regionCode,
            LocalDateTime slotStartTime) {
        return notificationSubscriptionRepository.findNotifiableByCategoryRegion(
                categoryId, regionCode, slotStartTime, now());
    }

    // 희망 시간이 하나라도 있으면 문자 알림 대상이 되므로 수신 동의를 요구한다.
    // 둘 다 없는 업장 찜은 알림 조회(START_TIME <= 빈자리 시각)에 걸리지 않아 문자가 나가지 않는다.
    private void requireSmsAgreementIfNotifying(AuthUser loginUser, OffsetDateTime startTime,
            OffsetDateTime endTime) {
        if (startTime != null || endTime != null) {
            termsAgreementService.requireAgreed(loginUser.userId(), TermsCode.SMS_NOTIFICATION);
        }
    }

    // 다른 고객의 구독과 이미 해제한 구독은 둘 다 없는 것으로 처리한다.
    private NotificationSubscription getActiveSubscription(AuthUser loginUser, Long subscriptionId) {
        NotificationSubscription subscription = notificationSubscriptionRepository
                .findByIdAndUserId(subscriptionId, loginUser.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
        if (!subscription.isActive()) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
        }
        return subscription;
    }

    // 희망 종료는 현재보다 뒤여야 한다. 같거나 지난 시각은 만료다. 시간이 없는 업장 찜은 만료가 아니다.
    private void validateNotExpired(LocalDateTime endTime) {
        if (endTime == null) {
            return;
        }
        LocalDateTime current = now();
        if (endTime.isBefore(current) || endTime.isEqual(current)) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_INVALID_TIME);
        }
    }

    /**
     * ACTIVE 구독 중 같은 대상·같은 희망 시간(시작·종료 둘 다 동일)이 있으면 막는다.
     * INACTIVE 는 다시 등록할 수 있다.
     *
     * @param excludeId 수정 시 자기 구독 ID. 등록 시에는 null
     */
    private void validateNoDuplicate(Long userId, SubscriptionType subscriptionType, Long shopId,
            Long categoryId, String regionCode, LocalDateTime startTime, LocalDateTime endTime,
            Long excludeId) {
        boolean duplicated;
        if (subscriptionType == SubscriptionType.STORE && startTime == null && endTime == null) {
            if (excludeId == null) {
                duplicated = notificationSubscriptionRepository
                        .existsByUserIdAndStatusAndShopIdAndStartTimeIsNullAndEndTimeIsNull(
                                userId, SubscriptionStatus.ACTIVE, shopId);
            } else {
                duplicated = notificationSubscriptionRepository
                        .existsByUserIdAndStatusAndShopIdAndStartTimeIsNullAndEndTimeIsNullAndIdNot(
                                userId, SubscriptionStatus.ACTIVE, shopId, excludeId);
            }
        } else if (subscriptionType == SubscriptionType.STORE) {
            if (excludeId == null) {
                duplicated = notificationSubscriptionRepository
                        .existsByUserIdAndStatusAndShopIdAndStartTimeAndEndTime(
                                userId, SubscriptionStatus.ACTIVE, shopId, startTime, endTime);
            } else {
                duplicated = notificationSubscriptionRepository
                        .existsByUserIdAndStatusAndShopIdAndStartTimeAndEndTimeAndIdNot(
                                userId, SubscriptionStatus.ACTIVE, shopId, startTime, endTime, excludeId);
            }
        } else {
            // CATEGORY_REGION
            if (excludeId == null) {
                duplicated = notificationSubscriptionRepository
                        .existsByUserIdAndStatusAndCategoryIdAndRegionCodeAndStartTimeAndEndTime(
                                userId, SubscriptionStatus.ACTIVE, categoryId, regionCode, startTime, endTime);
            } else {
                duplicated = notificationSubscriptionRepository
                        .existsByUserIdAndStatusAndCategoryIdAndRegionCodeAndStartTimeAndEndTimeAndIdNot(
                                userId, SubscriptionStatus.ACTIVE, categoryId, regionCode, startTime, endTime,
                                excludeId);
            }
        }
        if (duplicated) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_DUPLICATE);
        }
    }

    // 테스트에서 시각을 고정할 수 있게, 주입받은 Clock으로 현재 시각을 구한다.
    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
