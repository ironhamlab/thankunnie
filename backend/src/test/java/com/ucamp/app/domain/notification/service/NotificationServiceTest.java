package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ucamp.app.common.util.TimeUtils;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionCreateRequest;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionUpdateRequest;
import com.ucamp.app.domain.notification.dto.response.NotificationSubscriptionResponse;
import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionStatus;
import com.ucamp.app.domain.notification.entity.SubscriptionType;
import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), TimeUtils.KST);
    private static final AuthUser CUSTOMER = TestUser.CUSTOMER_1.toAuthUser();
    private static final AuthUser OWNER = TestUser.OWNER_1.toAuthUser();

    private final OffsetDateTime start = OffsetDateTime.now(CLOCK).plusDays(1).withNano(0);
    private final OffsetDateTime end = start.plusHours(3);

    @Mock
    private NotificationSubscriptionRepository repository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(repository, CLOCK);
    }

    @Test
    @DisplayName("업장 구독을 등록하면 매장 ID 와 희망 시간이 ACTIVE 상태로 저장된다")
    void subscribe_store_savesActiveSubscription() {
        given(repository.save(any(NotificationSubscription.class))).willAnswer(invocation -> invocation.getArgument(0));

        NotificationSubscriptionResponse response = notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, null, null, start, end));

        assertThat(response.subscriptionType()).isEqualTo(SubscriptionType.STORE);
        assertThat(response.shopId()).isEqualTo(1L);
        assertThat(response.categoryId()).isNull();
        assertThat(response.startTime()).isEqualTo(start);
        assertThat(response.endTime()).isEqualTo(end);
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("카테고리 구독을 등록하면 카테고리 ID와 지역코드가 저장되고 매장 ID는 없다")
    void subscribe_categoryRegion_savesCategoryAndRegion() {
        given(repository.save(any(NotificationSubscription.class))).willAnswer(invocation -> invocation.getArgument(0));

        NotificationSubscriptionResponse response = notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.CATEGORY_REGION, null, 10L, "SEOUL_GANGNAM", start, end));

        assertThat(response.shopId()).isNull();
        assertThat(response.categoryId()).isEqualTo(10L);
        assertThat(response.regionCode()).isEqualTo("SEOUL_GANGNAM");
    }

    @Test
    @DisplayName("고객이 아니면 구독을 등록할 수 없다")
    void subscribe_owner_throwsForbidden() {
        assertErrorCode(() -> notificationService.subscribe(OWNER,
                createRequest(SubscriptionType.STORE, 1L, null, null, start, end)), ErrorCode.FORBIDDEN);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("업장 구독에 카테고리가 함께 오면 대상 오류다")
    void subscribe_storeWithCategory_throwsInvalidTarget() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 10L, null, start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
    }

    @Test
    @DisplayName("업장 구독에 매장 ID 가 없으면 대상 오류다")
    void subscribe_storeWithoutShop_throwsInvalidTarget() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, null, null, null, start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
    }

    @Test
    @DisplayName("카테고리 구독에 지역코드가 없으면 대상 오류다")
    void subscribe_categoryRegionWithoutRegion_throwsInvalidTarget() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.CATEGORY_REGION, null, 10L, " ", start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
    }

    @Test
    @DisplayName("카테고리 구독에 매장 ID가 있으면 대상 오류다")
    void subscribe_categoryRegionWithShop_throwsInvalidTarget() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.CATEGORY_REGION, 1L, 10L, "SEOUL_GANGNAM", start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
    }

    @Test
    @DisplayName("시작 시각이 종료 시각보다 늦으면 시간 오류다")
    void subscribe_startAfterEnd_throwsInvalidTime() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, null, null, end, start)),
                ErrorCode.SUBSCRIPTION_INVALID_TIME);
    }

    @Test
    @DisplayName("종료 시각이 이미 지났으면 시간 오류다")
    void subscribe_expiredEndTime_throwsInvalidTime() {
        OffsetDateTime pastStart = OffsetDateTime.now(CLOCK).minusDays(2);
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, null, null, pastStart, pastStart.plusHours(1))),
                ErrorCode.SUBSCRIPTION_INVALID_TIME);
    }

    @Test
    @DisplayName("본인 구독을 수정하면 대상과 희망 시간이 바뀐다")
    void updateSubscription_own_changesCondition() {
        NotificationSubscription subscription = storeSubscription();
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(subscription));
        OffsetDateTime newStart = start.plusDays(1);

        NotificationSubscriptionResponse response = notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(2L, null, null, newStart, newStart.plusHours(2)));

        assertThat(response.shopId()).isEqualTo(2L);
        assertThat(subscription.getStartTime()).isEqualTo(TimeUtils.toLocal(newStart));
        assertThat(subscription.getEndTime()).isEqualTo(TimeUtils.toLocal(newStart.plusHours(2)));
    }

    @Test
    @DisplayName("구독 유형에 맞지 않는 값으로 수정할 수 없다")
    void updateSubscription_typeMismatch_throwsInvalidTarget() {
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(storeSubscription()));

        assertErrorCode(() -> notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(null, 10L, "SEOUL_GANGNAM", start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
    }

    @Test
    @DisplayName("다른 고객의 구독은 수정할 수 없다")
    void updateSubscription_otherUser_throwsNotFound() {
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.empty());

        assertErrorCode(() -> notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(1L, null, null, start, end)),
                ErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    @DisplayName("본인 구독을 해제하면 INACTIVE 가 된다")
    void unsubscribe_own_deactivates() {
        NotificationSubscription subscription = storeSubscription();
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(subscription));

        notificationService.unsubscribe(CUSTOMER, 10L);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.INACTIVE);
    }

    @Test
    @DisplayName("이미 해제한 구독은 다시 해제·수정할 수 없다")
    void unsubscribe_alreadyInactive_throwsNotFound() {
        NotificationSubscription subscription = storeSubscription();
        subscription.deactivate();
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(subscription));

        assertErrorCode(() -> notificationService.unsubscribe(CUSTOMER, 10L), ErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    private NotificationSubscription storeSubscription() {
        return NotificationSubscription.builder()
                .userId(CUSTOMER.userId())
                .subscriptionType(SubscriptionType.STORE)
                .shopId(1L)
                .startTime(toLocal(start))
                .endTime(toLocal(end))
                .build();
    }

    private static NotificationSubscriptionCreateRequest createRequest(SubscriptionType type, Long shopId,
            Long categoryId, String regionCode, OffsetDateTime startTime, OffsetDateTime endTime) {
        return new NotificationSubscriptionCreateRequest(type, shopId, categoryId, regionCode, startTime, endTime);
    }

    private static LocalDateTime toLocal(OffsetDateTime time) {
        return TimeUtils.toLocal(time);
    }

    private static void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
