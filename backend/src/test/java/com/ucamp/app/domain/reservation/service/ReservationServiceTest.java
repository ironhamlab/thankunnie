package com.ucamp.app.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.policy.ReservationStatus;
import com.ucamp.app.domain.reservation.dto.request.ReservationCreateRequest;
import com.ucamp.app.domain.reservation.dto.response.ReservationCreateResponse;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;

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

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(BookingPolicy.ZONE).toInstant(), BookingPolicy.ZONE);
        reservationService = new ReservationService(reservationRepository, treatmentRepository, clock);
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
        verify(reservationRepository).lockShop(SHOP_ID);
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
    @DisplayName("같은 매장에서 시간을 잡고 있는 예약과 겹치면 VACANCY_TIME_TAKEN 예외가 발생한다")
    void createReservation_overlappingOccupied_throwsTimeTaken() {
        givenTreatment(TreatmentStatus.ACTIVE);
        Reservation existing = reservation(NOW.plusMinutes(3));
        given(reservationRepository.findOverlappingInShop(eq(SHOP_ID), any(), any(), anyCollection()))
                .willReturn(List.of(existing));

        assertErrorCode(() -> reservationService.createReservation(CUSTOMER, request(NOW.plusHours(11), null)),
                ErrorCode.VACANCY_TIME_TAKEN);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("겹치는 예약이 만료된 점유뿐이면 예약할 수 있다")
    void createReservation_overlappingExpiredHold_saves() {
        givenTreatment(TreatmentStatus.ACTIVE);
        Reservation expired = reservation(NOW.minusMinutes(1));
        given(reservationRepository.findOverlappingInShop(eq(SHOP_ID), any(), any(), anyCollection()))
                .willReturn(List.of(expired));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateResponse response = reservationService.createReservation(
                CUSTOMER, request(NOW.plusHours(11), null));

        assertThat(response.status()).isEqualTo(ReservationStatus.HOLD);
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
                Clock.fixed(now.atZone(BookingPolicy.ZONE).toInstant(), BookingPolicy.ZONE));

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
        return Treatment.builder()
                .id(TREATMENT_ID)
                .shopId(SHOP_ID)
                .name("젤 네일")
                .regularPrice(50_000)
                .price(30_000)
                .lastMinutePrice(18_000)
                .durationMinutes(60)
                .status(status)
                .build();
    }

    private static Reservation reservation(LocalDateTime holdExpiresAt) {
        return Reservation.builder()
                .userId(TestUser.CUSTOMER_2.toAuthUser().userId())
                .treatment(treatment(TreatmentStatus.ACTIVE))
                .startsAt(NOW.plusHours(11))
                .endsAt(NOW.plusHours(12))
                .appliedPrice(30_000)
                .deposit(20_000)
                .holdExpiresAt(holdExpiresAt)
                .build();
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
