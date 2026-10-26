package com.ucamp.app.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.ShopLock;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.domain.reservation.dto.request.ReservationCreateRequest;
import com.ucamp.app.domain.reservation.dto.response.ReservationCreateResponse;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.shop.entity.Shop;
import com.ucamp.app.domain.treatment.dto.request.TreatmentSaveRequest;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 9, 0);
    private static final Long TREATMENT_ID = 1L;
    private static final Long SHOP_ID = 1L;
    private static final AuthUser CUSTOMER = TestUser.CUSTOMER_1.toAuthUser();

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private ShopLock shopLock;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(BookingPolicy.ZONE).toInstant(), BookingPolicy.ZONE);
        reservationService = new ReservationService(
                reservationRepository, treatmentRepository, availabilityService, shopLock, clock);
    }

    @Test
    @DisplayName("일반 구간 예약은 판매가와 예약금 상한을 적용해 점유 상태로 저장한다")
    void createReservation_normalTier_savesHoldWithPriceAndDeposit() {
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateResponse response = reservationService.createReservation(
                CUSTOMER, request(NOW.plusHours(11), "손톱이 짧아요"));

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
        assertThat(saved.getCustomerRequest()).isEqualTo("손톱이 짧아요");
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
                CUSTOMER, request(NOW.plusHours(2), null));

        assertThat(response.appliedPrice()).isEqualTo(18_000);
        assertThat(response.deposit()).isEqualTo(18_000);
    }

    @Test
    @DisplayName("다른 시간대(+00:00)로 보낸 시작 일시도 한국 시각으로 바꿔 저장한다")
    void createReservation_utcOffset_convertsToSeoulTime() {
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateResponse response = reservationService.createReservation(CUSTOMER,
                new ReservationCreateRequest(TREATMENT_ID, OffsetDateTime.parse("2026-11-01T11:00:00Z"), null));

        assertThat(response.startsAt()).isEqualTo(OffsetDateTime.parse("2026-11-01T20:00:00+09:00"));
    }

    @Test
    @DisplayName("매장을 잠근 뒤 영업시간·열기·닫기·예약 겹침을 시술 소요 시간으로 검사한다")
    void createReservation_validatesAvailabilityAfterLock() {
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11), null));

        InOrder inOrder = inOrder(shopLock, availabilityService, reservationRepository);
        inOrder.verify(shopLock).lock(SHOP_ID);
        inOrder.verify(availabilityService).validateAvailable(SHOP_ID, NOW.plusHours(11), 60);
        inOrder.verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("같은 매장에서 시간을 잡고 있는 예약과 겹치면 VACANCY_TIME_TAKEN 예외가 발생한다")
    void createReservation_timeTaken_throwsTimeTaken() {
        givenTreatment(TreatmentStatus.ACTIVE);
        willThrow(new BusinessException(ErrorCode.VACANCY_TIME_TAKEN))
                .given(availabilityService).validateAvailable(SHOP_ID, NOW.plusHours(11), 60);

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11), null)),
                ErrorCode.VACANCY_TIME_TAKEN);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("영업 외 시간이나 사장님이 닫은 시간이면 VACANCY_NOT_OPEN_TIME 예외가 발생한다")
    void createReservation_notOpenTime_throwsNotOpenTime() {
        givenTreatment(TreatmentStatus.ACTIVE);
        willThrow(new BusinessException(ErrorCode.VACANCY_NOT_OPEN_TIME))
                .given(availabilityService).validateAvailable(SHOP_ID, NOW.plusHours(11), 60);

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11), null)),
                ErrorCode.VACANCY_NOT_OPEN_TIME);
        verify(reservationRepository, never()).save(any());
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
                reservationRepository, treatmentRepository, availabilityService, shopLock, clock);
        givenTreatment(TreatmentStatus.ACTIVE);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateResponse response = service.createReservation(CUSTOMER, request(NOW.plusHours(11), null));

        assertThat(response.holdExpiresAt())
                .isEqualTo(afterLock.plusMinutes(5).atZone(BookingPolicy.ZONE).toOffsetDateTime());
    }

    @Test
    @DisplayName("시술이 없으면 TREATMENT_NOT_FOUND 예외가 발생한다")
    void createReservation_treatmentMissing_throwsNotFound() {
        given(treatmentRepository.findById(TREATMENT_ID)).willReturn(Optional.empty());

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11), null)),
                ErrorCode.TREATMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("판매 중이 아닌 시술이면 TREATMENT_NOT_FOUND 예외가 발생한다")
    void createReservation_inactiveTreatment_throwsNotFound() {
        givenTreatment(TreatmentStatus.INACTIVE);

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11), null)),
                ErrorCode.TREATMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("시작 20분 전 이후에는 VACANCY_BOOKING_CLOSED 예외가 발생한다")
    void createReservation_afterBookingDeadline_throwsBookingClosed() {
        // 09:10 에 09:30 시작을 클릭하면 시작까지 20분이라 마감이다
        LocalDateTime now = NOW.plusMinutes(10);
        ReservationService service = new ReservationService(reservationRepository, treatmentRepository,
                availabilityService, shopLock, Clock.fixed(now.atZone(BookingPolicy.ZONE).toInstant(), BookingPolicy.ZONE));

        assertErrorCode(() -> service.createReservation(CUSTOMER, request(NOW.plusMinutes(30), null)),
                ErrorCode.VACANCY_BOOKING_CLOSED);
    }

    @Test
    @DisplayName("고객이 아니면 FORBIDDEN 예외가 발생한다")
    void createReservation_owner_throwsForbidden() {
        assertErrorCode(() -> reservationService.createReservation(
                TestUser.OWNER_1.toAuthUser(), request(NOW.plusHours(11), null)), ErrorCode.FORBIDDEN);
    }

    private void givenTreatment(TreatmentStatus status) {
        given(treatmentRepository.findById(TREATMENT_ID)).willReturn(Optional.of(treatment(status)));
    }

    private static Treatment treatment(TreatmentStatus status) {
        Shop shop = BeanUtils.instantiateClass(Shop.class);
        ReflectionTestUtils.setField(shop, "shopId", SHOP_ID);
        Treatment treatment = Treatment.create(shop, null,
                new TreatmentSaveRequest("젤 네일", null, 50_000, 30_000, 18_000, 1L, 60));
        ReflectionTestUtils.setField(treatment, "treatmentId", TREATMENT_ID);
        treatment.changeStatus(status);
        return treatment;
    }

    private static ReservationCreateRequest request(LocalDateTime startsAt, String customerRequest) {
        return new ReservationCreateRequest(TREATMENT_ID,
                startsAt.atZone(BookingPolicy.ZONE).toOffsetDateTime(), customerRequest);
    }

    private static void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
