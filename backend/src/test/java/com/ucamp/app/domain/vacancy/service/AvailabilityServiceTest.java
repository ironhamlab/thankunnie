package com.ucamp.app.domain.vacancy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.schedule.entity.BusinessDay;
import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;
import com.ucamp.app.domain.schedule.entity.ShopBusinessHour;
import com.ucamp.app.domain.schedule.entity.ShopScheduleException;
import com.ucamp.app.domain.schedule.repository.ShopBusinessHourRepository;
import com.ucamp.app.domain.schedule.repository.ShopScheduleExceptionRepository;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    private static final long SHOP_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 25);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 9, 0);
    private static final int ONE_HOUR = 60;

    @Mock
    private ShopBusinessHourRepository shopBusinessHourRepository;

    @Mock
    private ShopScheduleExceptionRepository shopScheduleExceptionRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);
        service = new AvailabilityService(
                shopBusinessHourRepository, shopScheduleExceptionRepository, reservationRepository, fixedClock);
        given(shopBusinessHourRepository.findByShopId(SHOP_ID)).willReturn(List.of(ShopBusinessHour.builder()
                .shopId(SHOP_ID)
                .dayOfWeek(BusinessDay.of(DATE))
                .closed(false)
                .openTime(LocalTime.of(10, 0))
                .closeTime(LocalTime.of(20, 0))
                .build()));
    }

    @Test
    @DisplayName("영업시간 안의 빈 시각이면 예약할 수 있다")
    void validateAvailable_freeTime_passes() {
        assertThatCode(() -> service.validateAvailable(SHOP_ID, at(14, 0), ONE_HOUR)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시간을 잡고 있는 예약과 겹치면 이미 예약된 시간 예외가 발생한다")
    void validateAvailable_overlapsReservation_throwsTimeTaken() {
        given(reservationRepository.findActiveOverlapping(eq(SHOP_ID), any(), any()))
                .willReturn(List.of(reservation(14, 30, ReservationStatus.CONFIRMED)));

        assertThatThrownBy(() -> service.validateAvailable(SHOP_ID, at(14, 0), ONE_HOUR))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_TIME_TAKEN);
    }

    @Test
    @DisplayName("점유가 만료된 예약은 시간을 잡지 않는다")
    void validateAvailable_expiredHold_passes() {
        given(reservationRepository.findActiveOverlapping(eq(SHOP_ID), any(), any()))
                .willReturn(List.of(reservation(14, 0, ReservationStatus.HOLD)));

        assertThatCode(() -> service.validateAvailable(SHOP_ID, at(14, 0), ONE_HOUR)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("영업시간 밖이나 닫기와 겹치면 영업하지 않는 시간 예외가 발생한다")
    void validateAvailable_outsideOpenTime_throwsNotOpenTime() {
        given(shopScheduleExceptionRepository.findOverlapping(eq(SHOP_ID), any(), any()))
                .willReturn(List.of(ShopScheduleException.builder()
                        .shopId(SHOP_ID)
                        .exceptionType(ScheduleExceptionType.CLOSE)
                        .startsAt(at(12, 0))
                        .endsAt(at(13, 0))
                        .build()));

        assertThatThrownBy(() -> service.validateAvailable(SHOP_ID, at(19, 30), ONE_HOUR))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_NOT_OPEN_TIME);
        assertThatThrownBy(() -> service.validateAvailable(SHOP_ID, at(12, 30), ONE_HOUR))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_NOT_OPEN_TIME);
    }

    @Test
    @DisplayName("시간대 열기로 영업시간 뒤에도 예약할 수 있다")
    void getAvailableStartTimes_withOpenException_includesExtendedHours() {
        given(shopScheduleExceptionRepository.findOverlapping(eq(SHOP_ID), any(), any()))
                .willReturn(List.of(ShopScheduleException.builder()
                        .shopId(SHOP_ID)
                        .exceptionType(ScheduleExceptionType.OPEN)
                        .startsAt(at(20, 0))
                        .endsAt(at(22, 0))
                        .build()));

        List<LocalDateTime> result = service.getAvailableStartTimes(SHOP_ID, DATE, ONE_HOUR);

        assertThat(result).startsWith(at(10, 0)).endsWith(at(21, 0));
    }

    private static LocalDateTime at(int hour, int minute) {
        return DATE.atTime(hour, minute);
    }

    private static Reservation reservation(int hour, int minute, ReservationStatus status) {
        Reservation reservation = Reservation.builder()
                .userId(1L)
                .treatmentId(1L)
                .startsAt(at(hour, minute))
                .endsAt(at(hour, minute).plusHours(1))
                .appliedPrice(20_000)
                .deposit(20_000)
                .holdExpiresAt(NOW.minusMinutes(1))
                .build();
        // 상태 변경 메서드는 예약 담당(D)이 추가하므로 테스트에서는 직접 넣는다
        ReflectionTestUtils.setField(reservation, "status", status);
        return reservation;
    }
}
