package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.common.util.TimeUtils;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.UserLock;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionCreateRequest;
import com.ucamp.app.domain.notification.dto.request.NotificationSubscriptionUpdateRequest;
import com.ucamp.app.domain.notification.dto.response.NotificationSubscriptionResponse;
import com.ucamp.app.domain.notification.entity.NotificationSubscription;
import com.ucamp.app.domain.notification.entity.SubscriptionStatus;
import com.ucamp.app.domain.notification.entity.SubscriptionType;
import com.ucamp.app.domain.notification.repository.NotificationSubscriptionRepository;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;
import com.ucamp.app.domain.treatment.entity.TreatmentType;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;
import com.ucamp.app.domain.user.entity.AppUserStatus;
import com.ucamp.app.domain.user.entity.TermsCode;
import com.ucamp.app.domain.user.repository.AppUserRepository;
import com.ucamp.app.domain.user.service.TermsAgreementService;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), TimeUtils.KST);
    private static final AuthUser CUSTOMER = TestUser.CUSTOMER_1.toAuthUser();
    private static final AuthUser OWNER = TestUser.OWNER_1.toAuthUser();

    private final OffsetDateTime start = OffsetDateTime.now(CLOCK).plusDays(1).withNano(0);
    private final OffsetDateTime end = start.plusHours(3);

    @Mock
    private NotificationSubscriptionRepository repository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private StoreSubscriptionDispatchService storeSubscriptionDispatchService;

    @Mock
    private CategoryRegionSubscriptionDispatchService categoryRegionSubscriptionDispatchService;

    @Mock
    private TermsAgreementService termsAgreementService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private UserLock userLock;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                repository, treatmentRepository, storeSubscriptionDispatchService,
                categoryRegionSubscriptionDispatchService, CLOCK, termsAgreementService,
                appUserRepository, userLock);
        // 기본은 탈퇴하지 않은 고객. 잠금 전에 검사에서 끝나는 테스트도 있어 lenient 로 둔다
        lenient().when(appUserRepository.existsByUserIdAndStatus(CUSTOMER.userId(), AppUserStatus.ACTIVE))
                .thenReturn(true);
        Treatment treatment = mock(Treatment.class);
        lenient().when(treatment.getStatus()).thenReturn(TreatmentStatus.ACTIVE);
        lenient().when(treatment.getTreatmentType()).thenReturn(TreatmentType.NORMAL);
        lenient().when(treatmentRepository.findByTreatmentIdAndShopShopId(any(), any()))
                .thenReturn(Optional.of(treatment));
    }

    @Test
    @DisplayName("업장 알림을 등록하면 매장 ID 와 희망 시간이 ACTIVE 상태로 저장된다")
    void subscribe_store_savesActiveSubscription() {
        given(repository.existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTime(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(1L), eq(100L),
                eq(toLocal(start)), eq(toLocal(end)))).willReturn(false);
        given(repository.save(any(NotificationSubscription.class))).willAnswer(invocation -> invocation.getArgument(0));

        NotificationSubscriptionResponse response = notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 100L, null, null, start, end));

        assertThat(response.subscriptionType()).isEqualTo(SubscriptionType.STORE);
        assertThat(response.shopId()).isEqualTo(1L);
        assertThat(response.treatmentId()).isEqualTo(100L);
        assertThat(response.categoryId()).isNull();
        assertThat(response.startTime()).isEqualTo(start);
        assertThat(response.endTime()).isEqualTo(end);
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(storeSubscriptionDispatchService).enqueue(any(NotificationSubscription.class));
    }

    @Test
    @DisplayName("카테고리 알림을 등록하면 카테고리 ID와 지역코드가 저장되고 매장 ID는 없다")
    void subscribe_categoryRegion_savesCategoryAndRegion() {
        given(repository.existsByUserIdAndStatusAndCategoryIdAndRegionCodeAndStartTimeAndEndTime(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(10L), eq("SEOUL_GANGNAM"),
                eq(toLocal(start)), eq(toLocal(end)))).willReturn(false);
        given(repository.save(any(NotificationSubscription.class))).willAnswer(invocation -> invocation.getArgument(0));

        NotificationSubscriptionResponse response = notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.CATEGORY_REGION, null, null, 10L, "SEOUL_GANGNAM", start, end));

        assertThat(response.shopId()).isNull();
        assertThat(response.treatmentId()).isNull();
        assertThat(response.categoryId()).isEqualTo(10L);
        assertThat(response.regionCode()).isEqualTo("SEOUL_GANGNAM");
        verify(storeSubscriptionDispatchService, never()).enqueue(any());
        verify(categoryRegionSubscriptionDispatchService).enqueue(any(NotificationSubscription.class));
    }

    @Test
    @DisplayName("같은 매장·같은 시술·같은 희망 시간의 ACTIVE 알림이 있으면 등록할 수 없다")
    void subscribe_duplicateStore_throwsDuplicate() {
        given(repository.existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTime(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(1L), eq(100L),
                eq(toLocal(start)), eq(toLocal(end)))).willReturn(true);

        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 100L, null, null, start, end)),
                ErrorCode.SUBSCRIPTION_DUPLICATE);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("같은 카테고리·지역·희망 시간의 ACTIVE 알림이 있으면 등록할 수 없다")
    void subscribe_duplicateCategoryRegion_throwsDuplicate() {
        given(repository.existsByUserIdAndStatusAndCategoryIdAndRegionCodeAndStartTimeAndEndTime(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(10L), eq("SEOUL_GANGNAM"),
                eq(toLocal(start)), eq(toLocal(end)))).willReturn(true);

        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.CATEGORY_REGION, null, null, 10L, "SEOUL_GANGNAM", start, end)),
                ErrorCode.SUBSCRIPTION_DUPLICATE);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("알림 문자 수신 동의가 없으면 TERMS_SMS_AGREEMENT_REQUIRED 예외가 발생하고 저장하지 않는다")
    void subscribe_withoutSmsAgreement_throwsAgreementRequired() {
        willThrow(new BusinessException(ErrorCode.TERMS_SMS_AGREEMENT_REQUIRED))
                .given(termsAgreementService).requireAgreed(CUSTOMER.userId(), TermsCode.SMS_NOTIFICATION);

        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 100L, null, null, start, end)),
                ErrorCode.TERMS_SMS_AGREEMENT_REQUIRED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("알림 등록 시 알림 문자 수신 동의를 검사한다")
    void subscribe_checksSmsAgreement() {
        given(repository.existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTime(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(1L), eq(100L),
                eq(toLocal(start)), eq(toLocal(end)))).willReturn(false);
        given(repository.save(any(NotificationSubscription.class))).willAnswer(invocation -> invocation.getArgument(0));

        notificationService.subscribe(CUSTOMER, createRequest(SubscriptionType.STORE, 1L, 100L, null, null, start, end));

        verify(termsAgreementService).requireAgreed(CUSTOMER.userId(), TermsCode.SMS_NOTIFICATION);
    }

    @Test
    @DisplayName("고객이 아니면 알림을 등록할 수 없다")
    void subscribe_owner_throwsForbidden() {
        assertErrorCode(() -> notificationService.subscribe(OWNER,
                createRequest(SubscriptionType.STORE, 1L, 100L, null, null, start, end)), ErrorCode.FORBIDDEN);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("업장 알림에 카테고리가 함께 오면 대상 오류다")
    void subscribe_storeWithCategory_throwsInvalidTarget() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 100L, 10L, null, start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
        // 대상이 틀리면 중복 조회까지 가지 않는다
        verify(repository, never()).existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTime(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("희망 시간이 있는 업장 알림에 시술 ID 가 없으면 대상 오류다")
    void subscribe_storeWithoutTreatment_throwsInvalidTarget() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, null, null, null, start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
        verify(treatmentRepository, never()).findByTreatmentIdAndShopShopId(any(), any());
    }

    @Test
    @DisplayName("그 매장에 없는 시술이면 고를 수 없다")
    void subscribe_unknownTreatment_throwsInvalidTreatment() {
        given(treatmentRepository.findByTreatmentIdAndShopShopId(100L, 1L)).willReturn(Optional.empty());

        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 100L, null, null, start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TREATMENT);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("초보 시술은 고를 수 없다")
    void subscribe_modelTreatment_throwsInvalidTreatment() {
        Treatment model = mock(Treatment.class);
        given(model.getStatus()).willReturn(TreatmentStatus.ACTIVE);
        given(model.getTreatmentType()).willReturn(TreatmentType.MODEL);
        given(treatmentRepository.findByTreatmentIdAndShopShopId(100L, 1L)).willReturn(Optional.of(model));

        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 100L, null, null, start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TREATMENT);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("판매 중이 아닌 시술은 고를 수 없다")
    void subscribe_inactiveTreatment_throwsInvalidTreatment() {
        Treatment inactive = mock(Treatment.class);
        given(inactive.getStatus()).willReturn(TreatmentStatus.INACTIVE);
        given(treatmentRepository.findByTreatmentIdAndShopShopId(100L, 1L)).willReturn(Optional.of(inactive));

        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 100L, null, null, start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TREATMENT);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("카테고리 알림에 시술 ID가 있으면 대상 오류다")
    void subscribe_categoryRegionWithTreatment_throwsInvalidTarget() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.CATEGORY_REGION, null, 100L, 10L, "SEOUL_GANGNAM", start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
    }

    @Test
    @DisplayName("업장 알림에 매장 ID 가 없으면 대상 오류다")
    void subscribe_storeWithoutShop_throwsInvalidTarget() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, null, 100L, null, null, start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
    }

    @Test
    @DisplayName("카테고리 알림에 지역코드가 없으면 대상 오류다")
    void subscribe_categoryRegionWithoutRegion_throwsInvalidTarget() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.CATEGORY_REGION, null, null, 10L, " ", start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
    }

    @Test
    @DisplayName("카테고리 알림에 매장 ID가 있으면 대상 오류다")
    void subscribe_categoryRegionWithShop_throwsInvalidTarget() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.CATEGORY_REGION, 1L, null, 10L, "SEOUL_GANGNAM", start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
    }

    @Test
    @DisplayName("시작 시각이 종료 시각보다 늦으면 시간 오류다")
    void subscribe_startAfterEnd_throwsInvalidTime() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 100L, null, null, end, start)),
                ErrorCode.SUBSCRIPTION_INVALID_TIME);
    }

    @Test
    @DisplayName("업장 찜은 희망 시간 없이 저장되고 알림 시간은 비어 있다")
    void subscribe_storeWithoutTime_savesFavorite() {
        given(repository.existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeIsNullAndEndTimeIsNull(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(1L), isNull())).willReturn(false);
        given(repository.save(any(NotificationSubscription.class))).willAnswer(invocation -> invocation.getArgument(0));

        NotificationSubscriptionResponse response = notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, null, null, null, null, null));

        assertThat(response.shopId()).isEqualTo(1L);
        assertThat(response.treatmentId()).isNull();
        assertThat(response.startTime()).isNull();
        assertThat(response.endTime()).isNull();
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        // 시간 없는 찜은 시술을 아직 고르지 않고, 문자가 나가지 않으므로 동의를 검사하지 않는다
        verify(treatmentRepository, never()).findByTreatmentIdAndShopShopId(any(), any());
        verify(termsAgreementService, never()).requireAgreed(any(), any());
        verify(storeSubscriptionDispatchService, never()).enqueue(any());
    }

    @Test
    @DisplayName("같은 매장·같은 시술의 시간 없는 찜이 있으면 다시 등록할 수 없다")
    void subscribe_duplicateFavorite_throwsDuplicate() {
        given(repository.existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeIsNullAndEndTimeIsNull(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(1L), isNull())).willReturn(true);

        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, null, null, null, null, null)),
                ErrorCode.SUBSCRIPTION_DUPLICATE);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("카테고리 알림은 희망 시간 없이 등록할 수 없다")
    void subscribe_categoryRegionWithoutTime_throwsInvalidTime() {
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.CATEGORY_REGION, null, null, 10L, "SEOUL_GANGNAM", null, null)),
                ErrorCode.SUBSCRIPTION_INVALID_TIME);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("시간 없는 업장 찜에 희망 시간을 넣으면 그 알림의 시간이 바뀐다")
    void updateSubscription_favorite_setsHopeTime() {
        NotificationSubscription subscription = NotificationSubscription.builder()
                .userId(CUSTOMER.userId())
                .subscriptionType(SubscriptionType.STORE)
                .treatmentId(100L)
                .shopId(1L)
                .startTime(null)
                .endTime(null)
                .build();
        ReflectionTestUtils.setField(subscription, "id", 10L);
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(subscription));
        given(repository.existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTimeAndIdNot(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(1L), eq(100L),
                eq(toLocal(start)), eq(toLocal(end)), eq(10L))).willReturn(false);

        NotificationSubscriptionResponse response = notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(1L, 100L, null, null, start, end));

        assertThat(response.startTime()).isEqualTo(start);
        assertThat(response.endTime()).isEqualTo(end);
        assertThat(subscription.getStartTime()).isEqualTo(toLocal(start));
        verify(termsAgreementService).requireAgreed(CUSTOMER.userId(), TermsCode.SMS_NOTIFICATION);
        verify(storeSubscriptionDispatchService).enqueue(subscription);
    }

    @Test
    @DisplayName("문자 수신 동의 없이 찜에 희망 시간을 넣으면 TERMS_SMS_AGREEMENT_REQUIRED 예외가 발생하고 중복 검사 전에 멈춘다")
    void updateSubscription_favoriteWithoutSmsAgreement_throwsAgreementRequired() {
        NotificationSubscription subscription = NotificationSubscription.builder()
                .userId(CUSTOMER.userId())
                .subscriptionType(SubscriptionType.STORE)
                .treatmentId(100L)
                .shopId(1L)
                .startTime(null)
                .endTime(null)
                .build();
        ReflectionTestUtils.setField(subscription, "id", 10L);
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(subscription));
        willThrow(new BusinessException(ErrorCode.TERMS_SMS_AGREEMENT_REQUIRED))
                .given(termsAgreementService).requireAgreed(CUSTOMER.userId(), TermsCode.SMS_NOTIFICATION);

        assertErrorCode(() -> notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(1L, 100L, null, null, start, end)),
                ErrorCode.TERMS_SMS_AGREEMENT_REQUIRED);
        // 중복 검사 전에 멈추는 것까지만 확인한다. 바꾼 조건이 저장되지 않는 것은 운영에서 트랜잭션 롤백이 보장하며,
        // 이 단위 테스트는 스프링 트랜잭션 없이 서비스를 직접 만들어 롤백 자체는 검증하지 않는다
        verify(repository, never()).existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTimeAndIdNot(
                any(), any(), any(), any(), any(), any(), any());
        verify(storeSubscriptionDispatchService, never()).enqueue(any());
    }

    @Test
    @DisplayName("종료 시각이 이미 지났으면 시간 오류다")
    void subscribe_expiredEndTime_throwsInvalidTime() {
        OffsetDateTime pastStart = OffsetDateTime.now(CLOCK).minusDays(2);
        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 100L, null, null, pastStart, pastStart.plusHours(1))),
                ErrorCode.SUBSCRIPTION_INVALID_TIME);
    }

    @Test
    @DisplayName("본인 알림을 수정하면 대상과 희망 시간이 바뀐다")
    void updateSubscription_own_changesCondition() {
        NotificationSubscription subscription = storeSubscription();
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(subscription));
        OffsetDateTime newStart = start.plusDays(1);
        LocalDateTime newStartLocal = toLocal(newStart);
        LocalDateTime newEndLocal = toLocal(newStart.plusHours(2));
        // ACTIVE 만 보고, 자기 알림 id=10 은 중복에서 뺀다
        given(repository.existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTimeAndIdNot(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(2L), eq(100L),
                eq(newStartLocal), eq(newEndLocal), eq(10L))).willReturn(false);

        NotificationSubscriptionResponse response = notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(2L, 100L, null, null, newStart, newStart.plusHours(2)));

        assertThat(response.shopId()).isEqualTo(2L);
        assertThat(subscription.getStartTime()).isEqualTo(newStartLocal);
        assertThat(subscription.getEndTime()).isEqualTo(newEndLocal);
        verify(repository).existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTimeAndIdNot(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(2L), eq(100L),
                eq(newStartLocal), eq(newEndLocal), eq(10L));
        verify(storeSubscriptionDispatchService).enqueue(subscription);
    }

    @Test
    @DisplayName("수정 결과가 다른 ACTIVE 알림과 같으면 수정할 수 없다")
    void updateSubscription_duplicateOther_throwsDuplicate() {
        NotificationSubscription subscription = storeSubscription();
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(subscription));
        given(repository.existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTimeAndIdNot(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(2L), eq(100L),
                eq(toLocal(start)), eq(toLocal(end)), eq(10L))).willReturn(true);

        assertErrorCode(() -> notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(2L, 100L, null, null, start, end)),
                ErrorCode.SUBSCRIPTION_DUPLICATE);
        verify(repository).existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTimeAndIdNot(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(2L), eq(100L),
                eq(toLocal(start)), eq(toLocal(end)), eq(10L));
    }

    @Test
    @DisplayName("알림 유형에 맞지 않는 값으로 수정할 수 없다")
    void updateSubscription_typeMismatch_throwsInvalidTarget() {
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(storeSubscription()));

        assertErrorCode(() -> notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(null, null, 10L, "SEOUL_GANGNAM", start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
        // 대상이 틀리면 중복 조회까지 가지 않는다 (409 가 나면 안 됨)
        verify(repository, never()).existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTimeAndIdNot(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("STORE 수정에 카테고리가 섞여 있으면 매장 중복과 같아도 대상 오류다")
    void updateSubscription_invalidTarget_notDuplicate() {
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(storeSubscription()));

        // shopId=2 는 다른 알림과 겹칠 수 있는 값이지만, categoryId 가 있어 대상 오류가 먼저다
        assertErrorCode(() -> notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(2L, 100L, 10L, null, start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
        verify(repository, never()).existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTimeAndIdNot(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("다른 고객의 알림은 수정할 수 없다")
    void updateSubscription_otherUser_throwsNotFound() {
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.empty());

        assertErrorCode(() -> notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(1L, 100L, null, null, start, end)),
                ErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    @DisplayName("본인 알림을 해제하면 INACTIVE 가 된다")
    void unsubscribe_own_deactivates() {
        NotificationSubscription subscription = storeSubscription();
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(subscription));

        notificationService.unsubscribe(CUSTOMER, 10L);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.INACTIVE);
    }

    @Test
    @DisplayName("이미 해제한 알림은 다시 해제·수정할 수 없다")
    void unsubscribe_alreadyInactive_throwsNotFound() {
        NotificationSubscription subscription = storeSubscription();
        subscription.deactivate();
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(subscription));

        assertErrorCode(() -> notificationService.unsubscribe(CUSTOMER, 10L), ErrorCode.SUBSCRIPTION_NOT_FOUND);
    }

    @Test
    @DisplayName("문자 수신 동의가 없어도 매장 ID 가 빠진 업장 알림은 동의 필요가 아니라 대상 오류다")
    void subscribe_withoutSmsAgreementAndShop_throwsInvalidTargetFirst() {
        denySmsAgreement();

        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, null, 100L, null, null, start, end)),
                ErrorCode.SUBSCRIPTION_INVALID_TARGET);
        verify(termsAgreementService, never()).requireAgreed(any(), any());
    }

    @Test
    @DisplayName("문자 수신 동의가 없어도 시작이 종료보다 늦으면 동의 필요가 아니라 시간 오류다")
    void subscribe_withoutSmsAgreementAndStartAfterEnd_throwsInvalidTimeFirst() {
        denySmsAgreement();

        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 100L, null, null, end, start)),
                ErrorCode.SUBSCRIPTION_INVALID_TIME);
        verify(termsAgreementService, never()).requireAgreed(any(), any());
    }

    @Test
    @DisplayName("이미 알림이 켜진 알림은 문자 수신 동의 기록이 없어도 수정할 수 있다")
    void updateSubscription_alreadyNotifyingWithoutSmsAgreement_updates() {
        denySmsAgreement();
        NotificationSubscription subscription = storeSubscription();
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(subscription));
        OffsetDateTime newStart = start.plusDays(1);
        given(repository.existsByUserIdAndStatusAndShopIdAndTreatmentIdAndStartTimeAndEndTimeAndIdNot(
                eq(CUSTOMER.userId()), eq(SubscriptionStatus.ACTIVE), eq(1L), eq(100L),
                eq(toLocal(newStart)), eq(toLocal(newStart.plusHours(2))), eq(10L))).willReturn(false);

        notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(1L, 100L, null, null, newStart, newStart.plusHours(2)));

        assertThat(subscription.getStartTime()).isEqualTo(toLocal(newStart));
        verify(termsAgreementService, never()).requireAgreed(any(), any());
    }

    @Test
    @DisplayName("문자 수신 동의가 없어도 찜에 넣는 희망 시간이 잘못되면 동의 필요가 아니라 시간 오류다")
    void updateSubscription_favoriteWithoutSmsAgreementAndStartAfterEnd_throwsInvalidTimeFirst() {
        denySmsAgreement();
        NotificationSubscription subscription = NotificationSubscription.builder()
                .userId(CUSTOMER.userId())
                .subscriptionType(SubscriptionType.STORE)
                .treatmentId(100L)
                .shopId(1L)
                .startTime(null)
                .endTime(null)
                .build();
        ReflectionTestUtils.setField(subscription, "id", 10L);
        given(repository.findByIdAndUserId(10L, CUSTOMER.userId())).willReturn(Optional.of(subscription));

        assertErrorCode(() -> notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(1L, 100L, null, null, end, start)),
                ErrorCode.SUBSCRIPTION_INVALID_TIME);
        verify(termsAgreementService, never()).requireAgreed(any(), any());
    }

    @Test
    @DisplayName("토큰 검사 뒤 탈퇴가 먼저 끝났으면 회원 잠금 뒤 확인에서 막혀 알림을 저장하지 않는다")
    void subscribe_withdrawnAfterTokenCheck_throwsUnauthorized() {
        given(appUserRepository.existsByUserIdAndStatus(CUSTOMER.userId(), AppUserStatus.ACTIVE)).willReturn(false);

        assertErrorCode(() -> notificationService.subscribe(CUSTOMER,
                createRequest(SubscriptionType.STORE, 1L, 100L, null, null, start, end)),
                ErrorCode.UNAUTHORIZED);
        verify(userLock).lock(CUSTOMER.userId());
        verify(repository, never()).save(any());
        verify(storeSubscriptionDispatchService, never()).enqueue(any());
    }

    @Test
    @DisplayName("토큰 검사 뒤 탈퇴가 먼저 끝났으면 알림을 읽기 전에 막혀 해제된 알림을 되살리지 않는다")
    void updateSubscription_withdrawnAfterTokenCheck_throwsUnauthorized() {
        given(appUserRepository.existsByUserIdAndStatus(CUSTOMER.userId(), AppUserStatus.ACTIVE)).willReturn(false);

        assertErrorCode(() -> notificationService.updateSubscription(CUSTOMER, 10L,
                new NotificationSubscriptionUpdateRequest(1L, 100L, null, null, start, end)),
                ErrorCode.UNAUTHORIZED);
        verify(userLock).lock(CUSTOMER.userId());
        verify(repository, never()).findByIdAndUserId(any(), any());
    }

    // 동의 기록이 없는 고객. 검사까지 가지 않는 테스트도 있어 lenient 로 둔다
    private void denySmsAgreement() {
        lenient().doThrow(new BusinessException(ErrorCode.TERMS_SMS_AGREEMENT_REQUIRED))
                .when(termsAgreementService).requireAgreed(CUSTOMER.userId(), TermsCode.SMS_NOTIFICATION);
    }

    private NotificationSubscription storeSubscription() {
        NotificationSubscription subscription = NotificationSubscription.builder()
                .userId(CUSTOMER.userId())
                .subscriptionType(SubscriptionType.STORE)
                .treatmentId(100L)
                .shopId(1L)
                .startTime(toLocal(start))
                .endTime(toLocal(end))
                .build();
        // 빌더는 id 를 안 넣어서, 수정 테스트용으로 직접 넣는다
        ReflectionTestUtils.setField(subscription, "id", 10L);
        return subscription;
    }

    private static NotificationSubscriptionCreateRequest createRequest(SubscriptionType type, Long shopId,
            Long treatmentId, Long categoryId, String regionCode, OffsetDateTime startTime, OffsetDateTime endTime) {
        return new NotificationSubscriptionCreateRequest(
                type, shopId, treatmentId, categoryId, regionCode, startTime, endTime);
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
