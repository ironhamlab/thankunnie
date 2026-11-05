package com.ucamp.app.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.ShopLock;
import com.ucamp.app.common.lock.UserLock;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.domain.notification.event.ReservationConfirmedEvent;
import com.ucamp.app.domain.reservation.dto.request.ReservationCreateRequest;
import com.ucamp.app.domain.reservation.dto.response.ReservationCreateResponse;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.shop.entity.Shop;
import com.ucamp.app.domain.treatment.dto.request.TreatmentSaveRequest;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;
import com.ucamp.app.domain.treatment.entity.TreatmentType;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;
import com.ucamp.app.domain.user.entity.TermsCode;
import com.ucamp.app.domain.user.service.TermsAgreementService;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 9, 0);
    private static final Long TREATMENT_ID = 1L;
    private static final Long SHOP_ID = 1L;
    private static final Long RESERVATION_ID = 100L;
    private static final AuthUser CUSTOMER = TestUser.CUSTOMER_1.toAuthUser();

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private ShopLock shopLock;

    @Mock
    private UserLock userLock;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TermsAgreementService termsAgreementService;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(BookingPolicy.ZONE).toInstant(), BookingPolicy.ZONE);
        reservationService = new ReservationService(
                reservationRepository, treatmentRepository, availabilityService, shopLock, userLock, clock,
                eventPublisher, termsAgreementService);
    }

    @Test
    @DisplayName("일반 구간 예약은 판매가와 예약금 상한을 적용해 점유 상태로 저장한다")
    void createReservation_normalTier_savesHoldWithPriceAndDeposit() {
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateResponse response = reservationService.createReservation(
                CUSTOMER, request(NOW.plusHours(11)));

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        Reservation saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(CUSTOMER.userId());
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.HOLD);
        assertThat(saved.getStartsAt()).isEqualTo(NOW.plusHours(11));
        assertThat(saved.getEndsAt()).isEqualTo(NOW.plusHours(12));
        assertThat(saved.getAppliedPrice()).isEqualTo(30_000);
        assertThat(saved.getDeposit()).isEqualTo(BookingPolicy.MAX_DEPOSIT_AMOUNT);
        assertThat(saved.getHoldExpiresAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(saved.getCustomerRequest()).isNull();
        assertThat(response.treatmentId()).isEqualTo(TREATMENT_ID);
        assertThat(response.startsAt()).isEqualTo(OffsetDateTime.parse("2026-11-01T20:00:00+09:00"));
        verify(shopLock).lock(SHOP_ID);
    }

    @Test
    @DisplayName("초임박 구간 예약은 초임박 판매가를 적용하고 예약금은 판매가와 같다")
    void createReservation_lastMinuteTier_appliesLastMinutePrice() {
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateResponse response = reservationService.createReservation(
                CUSTOMER, request(NOW.plusHours(2)));

        assertThat(response.appliedPrice()).isEqualTo(18_000);
        assertThat(response.deposit()).isEqualTo(18_000);
    }

    @Test
    @DisplayName("다른 시간대(+00:00)로 보낸 시작 일시도 한국 시각으로 바꿔 저장한다")
    void createReservation_utcOffset_convertsToSeoulTime() {
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateResponse response = reservationService.createReservation(CUSTOMER,
                new ReservationCreateRequest(TREATMENT_ID, OffsetDateTime.parse("2026-11-01T11:00:00Z")));

        assertThat(response.startsAt()).isEqualTo(OffsetDateTime.parse("2026-11-01T20:00:00+09:00"));
    }

    @Test
    @DisplayName("회원·매장 순으로 잠근 뒤 영업시간·열기·닫기·예약 겹침과 본인 예약 겹침을 검사한다")
    void createReservation_validatesAvailabilityAfterLock() {
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11)));

        InOrder inOrder = inOrder(userLock, shopLock, availabilityService, reservationRepository);
        inOrder.verify(userLock).lock(CUSTOMER.userId());
        inOrder.verify(shopLock).lock(SHOP_ID);
        inOrder.verify(availabilityService).validateAvailable(SHOP_ID, NOW.plusHours(11), 60);
        inOrder.verify(reservationRepository).findActiveOverlappingByUser(
                CUSTOMER.userId(), NOW.plusHours(11), NOW.plusHours(12));
        inOrder.verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("같은 매장에서 시간을 잡고 있는 예약과 겹치면 VACANCY_TIME_TAKEN 예외가 발생한다")
    void createReservation_timeTaken_throwsTimeTaken() {
        givenTreatment(TreatmentStatus.ACTIVE);
        willThrow(new BusinessException(ErrorCode.VACANCY_TIME_TAKEN))
                .given(availabilityService).validateAvailable(SHOP_ID, NOW.plusHours(11), 60);

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11))),
                ErrorCode.VACANCY_TIME_TAKEN);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("영업 외 시간이나 매장이 닫은 시간이면 VACANCY_NOT_OPEN_TIME 예외가 발생한다")
    void createReservation_notOpenTime_throwsNotOpenTime() {
        givenTreatment(TreatmentStatus.ACTIVE);
        willThrow(new BusinessException(ErrorCode.VACANCY_NOT_OPEN_TIME))
                .given(availabilityService).validateAvailable(SHOP_ID, NOW.plusHours(11), 60);

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11))),
                ErrorCode.VACANCY_NOT_OPEN_TIME);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("본인이 같은 시간에 잡고 있는 예약이 있으면 매장이 달라도 VACANCY_USER_TIME_OVERLAP 예외가 발생한다")
    void createReservation_userOverlap_throwsUserTimeOverlap() {
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.findActiveOverlappingByUser(
                CUSTOMER.userId(), NOW.plusHours(11), NOW.plusHours(12)))
                .willReturn(List.of(userReservation(NOW.plusMinutes(5))));

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11))),
                ErrorCode.VACANCY_USER_TIME_OVERLAP);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("본인의 겹치는 점유가 이미 만료됐으면 예약할 수 있다")
    void createReservation_userOverlapExpiredHold_saves() {
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.findActiveOverlappingByUser(
                CUSTOMER.userId(), NOW.plusHours(11), NOW.plusHours(12)))
                .willReturn(List.of(userReservation(NOW)));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateResponse response = reservationService.createReservation(
                CUSTOMER, request(NOW.plusHours(11)));

        assertThat(response.status()).isEqualTo(ReservationStatus.HOLD);
    }

    @Test
    @DisplayName("점유 만료 시각은 매장 잠금을 얻은 뒤의 시각으로 계산한다")
    void createReservation_holdExpiresFromTimeAfterLock() {
        // 잠금 전 09:00, 잠금을 기다린 뒤 09:02 로 시계를 읽게 한다
        LocalDateTime afterLock = NOW.plusMinutes(2);
        Clock clock = mock(Clock.class);
        given(clock.getZone()).willReturn(BookingPolicy.ZONE);
        given(clock.instant()).willReturn(
                NOW.atZone(BookingPolicy.ZONE).toInstant(), afterLock.atZone(BookingPolicy.ZONE).toInstant());
        ReservationService service = new ReservationService(
                reservationRepository, treatmentRepository, availabilityService, shopLock, userLock, clock,
                eventPublisher, termsAgreementService);
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateResponse response = service.createReservation(CUSTOMER, request(NOW.plusHours(11)));

        assertThat(response.holdExpiresAt())
                .isEqualTo(afterLock.plusMinutes(5).atZone(BookingPolicy.ZONE).toOffsetDateTime());
    }

    @Test
    @DisplayName("시술이 없으면 TREATMENT_NOT_FOUND 예외가 발생한다")
    void createReservation_treatmentMissing_throwsNotFound() {
        given(treatmentRepository.findById(TREATMENT_ID)).willReturn(Optional.empty());

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11))),
                ErrorCode.TREATMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("판매 중이 아닌 시술이면 TREATMENT_NOT_FOUND 예외가 발생한다")
    void createReservation_inactiveTreatment_throwsNotFound() {
        givenTreatment(TreatmentStatus.INACTIVE);

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11))),
                ErrorCode.TREATMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("시작 20분 전 이후에는 VACANCY_BOOKING_CLOSED 예외가 발생한다")
    void createReservation_afterBookingDeadline_throwsBookingClosed() {
        // 09:10 에 09:30 시작을 클릭하면 시작까지 20분이라 마감이다
        LocalDateTime now = NOW.plusMinutes(10);
        ReservationService service = new ReservationService(reservationRepository, treatmentRepository,
                availabilityService, shopLock, userLock,
                Clock.fixed(now.atZone(BookingPolicy.ZONE).toInstant(), BookingPolicy.ZONE), eventPublisher,
                termsAgreementService);

        assertErrorCode(() -> service.createReservation(CUSTOMER, request(NOW.plusMinutes(30))),
                ErrorCode.VACANCY_BOOKING_CLOSED);
    }

    @Test
    @DisplayName("초보 시술인데 촬영 동의가 없으면 잠금 전에 TERMS_MODEL_PHOTO_AGREEMENT_REQUIRED 예외가 발생한다")
    void createReservation_modelWithoutPhotoAgreement_throwsBeforeLock() {
        Treatment treatment = treatment(TreatmentStatus.ACTIVE);
        ReflectionTestUtils.setField(treatment, "treatmentType", TreatmentType.MODEL);
        given(treatmentRepository.findById(TREATMENT_ID)).willReturn(Optional.of(treatment));
        willThrow(new BusinessException(ErrorCode.TERMS_MODEL_PHOTO_AGREEMENT_REQUIRED))
                .given(termsAgreementService).requireAgreed(CUSTOMER.userId(), TermsCode.MODEL_PHOTO);

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11))),
                ErrorCode.TERMS_MODEL_PHOTO_AGREEMENT_REQUIRED);
        verify(userLock, never()).lock(any());
        verify(shopLock, never()).lock(any());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    @DisplayName("초보 시술은 촬영 동의가 있으면 그대로 점유 예약을 만든다")
    void createReservation_modelWithPhotoAgreement_savesHold() {
        Treatment treatment = treatment(TreatmentStatus.ACTIVE);
        ReflectionTestUtils.setField(treatment, "treatmentType", TreatmentType.MODEL);
        given(treatmentRepository.findById(TREATMENT_ID)).willReturn(Optional.of(treatment));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateResponse response = reservationService.createReservation(
                CUSTOMER, request(NOW.plusHours(11)));

        assertThat(response.status()).isEqualTo(ReservationStatus.HOLD);
        verify(termsAgreementService).requireAgreed(CUSTOMER.userId(), TermsCode.MODEL_PHOTO);
    }

    @Test
    @DisplayName("일반 시술은 촬영 동의를 검사하지 않는다")
    void createReservation_normalTreatment_skipsPhotoAgreement() {
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11)));

        verify(termsAgreementService, never()).requireAgreed(any(), any());
    }

    @Test
    @DisplayName("고객이 아니면 FORBIDDEN 예외가 발생한다")
    void createReservation_owner_throwsForbidden() {
        assertErrorCode(() -> reservationService.createReservation(
                TestUser.OWNER_1.toAuthUser(), request(NOW.plusHours(11))), ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("결제창을 열기 전 검사: 본인의 유효한 점유면 통과하고 예약 상태는 바꾸지 않는다")
    void validatePayable_validHold_passes() {
        Reservation hold = userReservation(NOW.plusMinutes(5));
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(hold));

        reservationService.validatePayable(CUSTOMER.userId(), RESERVATION_ID);

        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.HOLD);
    }

    @Test
    @DisplayName("결제창을 열기 전 검사: 만료된 점유는 RESERVATION_003, 남의 예약은 RESERVATION_001 이다")
    void validatePayable_expiredOrOthers_throws() {
        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(userReservation(NOW.minusSeconds(1))));
        assertErrorCode(() -> reservationService.validatePayable(CUSTOMER.userId(), RESERVATION_ID),
                ErrorCode.RESERVATION_HOLD_EXPIRED);

        given(reservationRepository.findById(RESERVATION_ID))
                .willReturn(Optional.of(userReservation(NOW.plusMinutes(5))));
        assertErrorCode(() -> reservationService.validatePayable(CUSTOMER.userId() + 1, RESERVATION_ID),
                ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("본인 점유를 예약금만큼 결제하면 예약 행을 잠가 조회한 뒤 CONFIRMED 로 확정하고 요청사항을 저장한 뒤 확정 이벤트를 발행한다")
    void confirmReservation_validHold_confirms() {
        Reservation hold = userReservation(NOW.plusMinutes(5));
        given(reservationRepository.findByIdForUpdate(RESERVATION_ID)).willReturn(Optional.of(hold));

        Reservation confirmed = reservationService.confirmReservation(CUSTOMER.userId(), RESERVATION_ID, 20_000,
                "손톱은 라운드로 부탁드려요");

        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isEqualTo(NOW);
        assertThat(confirmed.getCustomerRequest()).isEqualTo("손톱은 라운드로 부탁드려요");
        verify(eventPublisher).publishEvent(new ReservationConfirmedEvent(RESERVATION_ID));
    }

    @Test
    @DisplayName("없는 예약이나 다른 고객의 예약을 확정하면 RESERVATION_NOT_FOUND 예외가 발생한다")
    void confirmReservation_missingOrOthers_throwsNotFound() {
        given(reservationRepository.findByIdForUpdate(RESERVATION_ID))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(userReservation(NOW.plusMinutes(5))));

        assertErrorCode(() -> reservationService.confirmReservation(CUSTOMER.userId(), RESERVATION_ID, 20_000, null),
                ErrorCode.RESERVATION_NOT_FOUND);
        assertErrorCode(() -> reservationService.confirmReservation(999L, RESERVATION_ID, 20_000, null),
                ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("결제 마감(시작 15분 전)이 지났으면 VACANCY_PAYMENT_CLOSED 예외가 발생하고 확정하지 않는다")
    void confirmReservation_afterPaymentDeadline_throwsWithoutConfirm() {
        // 점유 만료는 남았지만 시작 15분 전 이후인 비정상 데이터
        Reservation hold = Reservation.builder()
                .userId(CUSTOMER.userId())
                .treatmentId(TREATMENT_ID)
                .startsAt(NOW.plusMinutes(10))
                .endsAt(NOW.plusMinutes(70))
                .appliedPrice(20_000)
                .deposit(20_000)
                .holdExpiresAt(NOW.plusMinutes(5))
                .build();
        given(reservationRepository.findByIdForUpdate(RESERVATION_ID)).willReturn(Optional.of(hold));

        assertErrorCode(() -> reservationService.confirmReservation(CUSTOMER.userId(), RESERVATION_ID, 20_000, null),
                ErrorCode.VACANCY_PAYMENT_CLOSED);
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.HOLD);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("다른 시간의 본인 점유가 있는데 교체를 요청하지 않으면 RESERVATION_HOLD_EXISTS 예외가 발생하고 기존 점유는 그대로다")
    void createReservation_otherHoldWithoutReplace_throwsHoldExists() {
        givenTreatment(TreatmentStatus.ACTIVE);
        Reservation hold = userReservation(NOW.plusMinutes(5));
        givenHolds(hold);

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11))),
                ErrorCode.RESERVATION_HOLD_EXISTS);
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.HOLD);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 시술·같은 시각을 다시 누르면 교체 여부와 관계없이 기존 점유를 만료 시각 그대로 돌려준다")
    void createReservation_sameSlot_returnsExistingHold() {
        givenTreatment(TreatmentStatus.ACTIVE);
        Reservation hold = Reservation.builder()
                .userId(CUSTOMER.userId())
                .treatmentId(TREATMENT_ID)
                .startsAt(NOW.plusHours(11))
                .endsAt(NOW.plusHours(12))
                .appliedPrice(30_000)
                .deposit(20_000)
                .holdExpiresAt(NOW.plusMinutes(2))
                .build();
        givenHolds(hold);

        ReservationCreateResponse response = reservationService.createReservation(
                CUSTOMER, request(NOW.plusHours(11)));

        assertThat(response.holdExpiresAt())
                .isEqualTo(NOW.plusMinutes(2).atZone(BookingPolicy.ZONE).toOffsetDateTime());
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.HOLD);
        verify(availabilityService, never()).validateAvailable(any(), any(), anyInt());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("교체를 요청하면 기존 점유를 EXPIRED 로 바꾸고 반영한 뒤 가용 시간을 검사해 새 점유를 만든다")
    void createReservation_replaceHold_releasesThenCreates() {
        givenTreatment(TreatmentStatus.ACTIVE);
        Reservation hold = userReservation(NOW.plusMinutes(5));
        ReflectionTestUtils.setField(hold, "id", RESERVATION_ID);
        givenHolds(hold);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateResponse response = reservationService.createReservation(
                CUSTOMER, replaceRequest(NOW.plusHours(11), RESERVATION_ID));

        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(hold.getHoldExpiresAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(ReservationStatus.HOLD);
        InOrder inOrder = inOrder(userLock, reservationRepository, availabilityService);
        inOrder.verify(userLock).lock(CUSTOMER.userId());
        inOrder.verify(reservationRepository).findByUserIdAndStatusForUpdate(CUSTOMER.userId(), ReservationStatus.HOLD);
        inOrder.verify(reservationRepository).flush();
        inOrder.verify(availabilityService).validateAvailable(SHOP_ID, NOW.plusHours(11), 60);
        inOrder.verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("확인창에서 본 점유와 지금 점유가 다르면 풀지 않고 RESERVATION_HOLD_EXISTS 예외가 발생한다")
    void createReservation_replaceOtherHold_throwsHoldExists() {
        givenTreatment(TreatmentStatus.ACTIVE);
        Reservation hold = userReservation(NOW.plusMinutes(5));
        ReflectionTestUtils.setField(hold, "id", RESERVATION_ID);
        givenHolds(hold);

        assertErrorCode(() -> reservationService.createReservation(
                CUSTOMER, replaceRequest(NOW.plusHours(11), RESERVATION_ID + 1)), ErrorCode.RESERVATION_HOLD_EXISTS);
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.HOLD);
        verify(reservationRepository, never()).flush();
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("만료 시각이 지난 HOLD 는 점유로 보지 않아 교체 없이 새 점유를 만든다")
    void createReservation_staleHold_ignored() {
        givenTreatment(TreatmentStatus.ACTIVE);
        Reservation stale = userReservation(NOW);
        givenHolds(stale);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11)));

        assertThat(stale.getStatus()).isEqualTo(ReservationStatus.HOLD);
        verify(reservationRepository, never()).flush();
    }

    @Test
    @DisplayName("본인 점유를 풀면 예약 행을 잠가 조회한 뒤 EXPIRED 로 바꾸고 만료 시각을 지금으로 당긴다")
    void releaseHold_validHold_expires() {
        Reservation hold = userReservation(NOW.plusMinutes(5));
        given(reservationRepository.findByIdForUpdate(RESERVATION_ID)).willReturn(Optional.of(hold));

        reservationService.releaseHold(CUSTOMER, RESERVATION_ID);

        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(hold.getHoldExpiresAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("없는 예약이나 다른 고객의 예약을 풀면 RESERVATION_NOT_FOUND 예외가 발생한다")
    void releaseHold_missingOrOthers_throwsNotFound() {
        given(reservationRepository.findByIdForUpdate(RESERVATION_ID))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(userReservation(NOW.plusMinutes(5))));

        assertErrorCode(() -> reservationService.releaseHold(CUSTOMER, RESERVATION_ID),
                ErrorCode.RESERVATION_NOT_FOUND);
        assertErrorCode(() -> reservationService.releaseHold(TestUser.CUSTOMER_2.toAuthUser(), RESERVATION_ID),
                ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("고객이 아니면 점유를 풀 수 없다")
    void releaseHold_owner_throwsForbidden() {
        assertErrorCode(() -> reservationService.releaseHold(TestUser.OWNER_1.toAuthUser(), RESERVATION_ID),
                ErrorCode.FORBIDDEN);
        verify(reservationRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("점유 만료 정리는 현재 시각으로 만료된 HOLD 를 일괄 갱신하고 건수를 반환한다")
    void expireHolds_expiresWithNow() {
        given(reservationRepository.expireHolds(NOW)).willReturn(3);

        assertThat(reservationService.expireHolds()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(value = TreatmentStatus.class, names = {"INACTIVE", "DELETED"})
    @DisplayName("잠금 전에는 판매 중이었어도 잠금 획득 후 비활성화·삭제된 시술은 점유하지 않는다")
    void createReservation_statusChangedWhileWaiting_doesNotSave(TreatmentStatus status) {
        givenTreatment(TreatmentStatus.ACTIVE);
        // 잠금 전 엔티티는 ACTIVE로 남아 있는 상황을 재현한다.
        doAnswer(invocation -> {
            given(treatmentRepository.existsByTreatmentIdAndStatusNot(TREATMENT_ID, TreatmentStatus.ACTIVE))
                    .willReturn(status != TreatmentStatus.ACTIVE);
            return null;
        }).when(shopLock).lock(SHOP_ID);

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11))),
                ErrorCode.TREATMENT_NOT_FOUND);

        verify(reservationRepository, never()).save(any());
        verify(reservationRepository, never()).findByUserIdAndStatusForUpdate(any(), any());
    }

    @Test
    @DisplayName("삭제된 시술의 기존 점유는 결제창을 열 수 없다")
    void validatePayable_deletedTreatment_throwsNotFound() {
        Reservation hold = userReservation(NOW.plusMinutes(5));
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(hold));
        given(treatmentRepository.existsByTreatmentIdAndStatus(hold.getTreatmentId(), TreatmentStatus.DELETED))
                .willReturn(true);

        assertErrorCode(() -> reservationService.validatePayable(CUSTOMER.userId(), RESERVATION_ID),
                ErrorCode.TREATMENT_NOT_FOUND);
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.HOLD);
    }

    @Test
    @DisplayName("결제창을 연 뒤 삭제된 시술의 점유는 확정하거나 확정 이벤트를 발행하지 않는다")
    void confirmReservation_deletedTreatment_doesNotConfirm() {
        Reservation hold = userReservation(NOW.plusMinutes(5));
        given(reservationRepository.findByIdForUpdate(RESERVATION_ID)).willReturn(Optional.of(hold));
        given(treatmentRepository.existsByTreatmentIdAndStatus(hold.getTreatmentId(), TreatmentStatus.DELETED))
                .willReturn(true);

        assertErrorCode(() -> reservationService.confirmReservation(CUSTOMER.userId(), RESERVATION_ID,
                20_000, null), ErrorCode.TREATMENT_NOT_FOUND);
        assertThat(hold.getStatus()).isEqualTo(ReservationStatus.HOLD);
        verify(eventPublisher, never()).publishEvent(any());
    }

    private void givenTreatment(TreatmentStatus status) {
        given(treatmentRepository.findById(TREATMENT_ID)).willReturn(Optional.of(treatment(status)));
    }

    private static Treatment treatment(TreatmentStatus status) {
        Shop shop = BeanUtils.instantiateClass(Shop.class);
        ReflectionTestUtils.setField(shop, "shopId", SHOP_ID);
        Treatment treatment = Treatment.create(shop,
                new TreatmentSaveRequest("젤 네일", null, 50_000, 30_000, 18_000, 60));
        ReflectionTestUtils.setField(treatment, "treatmentId", TREATMENT_ID);
        treatment.changeStatus(status);
        return treatment;
    }

    // 다른 매장 시술의 본인 HOLD 예약. 만료 시각이 지금 이후면 아직 시간을 잡고 있다
    private static Reservation userReservation(LocalDateTime holdExpiresAt) {
        return Reservation.builder()
                .userId(CUSTOMER.userId())
                .treatmentId(99L)
                .startsAt(NOW.plusHours(11).plusMinutes(30))
                .endsAt(NOW.plusHours(12).plusMinutes(30))
                .appliedPrice(30_000)
                .deposit(20_000)
                .holdExpiresAt(holdExpiresAt)
                .build();
    }

    private void givenHolds(Reservation... holds) {
        given(reservationRepository.findByUserIdAndStatusForUpdate(CUSTOMER.userId(), ReservationStatus.HOLD))
                .willReturn(List.of(holds));
    }

    private static ReservationCreateRequest request(LocalDateTime startsAt) {
        return new ReservationCreateRequest(TREATMENT_ID, startsAt.atZone(BookingPolicy.ZONE).toOffsetDateTime());
    }

    private static ReservationCreateRequest replaceRequest(LocalDateTime startsAt, Long replaceReservationId) {
        return new ReservationCreateRequest(TREATMENT_ID, startsAt.atZone(BookingPolicy.ZONE).toOffsetDateTime(),
                replaceReservationId);
    }

    private static void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
