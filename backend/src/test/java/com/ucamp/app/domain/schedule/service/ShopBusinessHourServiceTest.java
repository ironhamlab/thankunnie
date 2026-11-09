package com.ucamp.app.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.ShopLock;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.schedule.dto.request.BusinessHourSaveRequest;
import com.ucamp.app.domain.schedule.dto.response.BusinessHourClosingImpactResponse;
import com.ucamp.app.domain.schedule.dto.response.BusinessHourResponse;
import com.ucamp.app.domain.schedule.entity.BusinessDay;
import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;
import com.ucamp.app.domain.schedule.entity.ShopBusinessHour;
import com.ucamp.app.domain.schedule.entity.ShopScheduleException;
import com.ucamp.app.domain.schedule.repository.ShopBusinessHourRepository;
import com.ucamp.app.domain.schedule.repository.ShopScheduleExceptionRepository;

@ExtendWith(MockitoExtension.class)
class ShopBusinessHourServiceTest {

    // 2026-10-25 는 일요일
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 9, 0);
    private static final LocalDateTime SUNDAY_14 = LocalDateTime.of(2026, 10, 25, 14, 0);

    private final AuthUser owner = TestUser.OWNER_1.toAuthUser();
    private final AuthUser customer = TestUser.CUSTOMER_1.toAuthUser();

    @Mock
    private ShopBusinessHourRepository shopBusinessHourRepository;

    @Mock
    private ShopScheduleExceptionRepository shopScheduleExceptionRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ShopLock shopLock;

    private ShopBusinessHourService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);
        service = new ShopBusinessHourService(shopBusinessHourRepository, shopScheduleExceptionRepository,
                reservationRepository, shopLock, fixedClock);
    }

    @Test
    @DisplayName("저장하지 않은 요일은 휴무로 월~일 7개를 반환한다")
    void getBusinessHours_noRows_returnsClosedWeek() {
        List<BusinessHourResponse> result = service.getBusinessHours(owner);

        assertThat(result).extracting(BusinessHourResponse::dayOfWeek).containsExactly(BusinessDay.values());
        assertThat(result).allMatch(BusinessHourResponse::closed);
    }

    @Test
    @DisplayName("처음 저장하면 7개 요일을 새로 만들고 휴무 요일은 시각을 지운다")
    void updateBusinessHours_firstTime_insertsSevenDays() {
        given(shopBusinessHourRepository.save(any(ShopBusinessHour.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        List<BusinessHourSaveRequest.Day> days = week(10, 20);
        days.set(6, new BusinessHourSaveRequest.Day(BusinessDay.SUN, true, LocalTime.of(10, 0), LocalTime.of(20, 0)));

        List<BusinessHourResponse> result = service.updateBusinessHours(owner, new BusinessHourSaveRequest(days));

        verify(shopLock).lock(owner.shopId());
        verify(shopBusinessHourRepository, times(7)).save(any(ShopBusinessHour.class));
        assertThat(result.get(0).openTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(result.get(6).closed()).isTrue();
        assertThat(result.get(6).openTime()).isNull();
    }

    @Test
    @DisplayName("이미 있는 요일은 새로 만들지 않고 값을 바꾼다")
    void updateBusinessHours_existing_changesInPlace() {
        List<ShopBusinessHour> existing = Arrays.stream(BusinessDay.values())
                .map(day -> hour(day, 9, 18))
                .toList();
        given(shopBusinessHourRepository.findByShopId(owner.shopId())).willReturn(existing);

        List<BusinessHourResponse> result =
                service.updateBusinessHours(owner, new BusinessHourSaveRequest(week(10, 20)));

        verify(shopBusinessHourRepository, never()).save(any());
        assertThat(existing.get(0).getOpenTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(result).hasSize(7);
    }

    @Test
    @DisplayName("요일이 빠지거나 중복되면 예외가 발생하고 잠그지 않는다")
    void updateBusinessHours_invalidDays_throwsInvalidBusinessDays() {
        List<BusinessHourSaveRequest.Day> duplicated = week(10, 20);
        duplicated.set(6, new BusinessHourSaveRequest.Day(BusinessDay.MON, false, LocalTime.of(10, 0),
                LocalTime.of(20, 0)));

        assertThatThrownBy(() -> service.updateBusinessHours(owner, new BusinessHourSaveRequest(duplicated)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_BUSINESS_DAYS);
        verify(shopLock, never()).lock(any());
    }

    @Test
    @DisplayName("영업일 시각 누락·30분 단위 아님·종료가 시작보다 앞이면 각각 예외가 발생한다")
    void updateBusinessHours_invalidTimes_throwsException() {
        assertThatThrownBy(() -> service.updateBusinessHours(owner, requestWithMonday(null, LocalTime.of(20, 0))))
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_BUSINESS_TIME_REQUIRED);
        assertThatThrownBy(() -> service.updateBusinessHours(owner,
                requestWithMonday(LocalTime.of(10, 15), LocalTime.of(20, 0))))
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_TIME_UNIT);
        assertThatThrownBy(() -> service.updateBusinessHours(owner,
                requestWithMonday(LocalTime.of(20, 0), LocalTime.of(10, 0))))
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_TIME_RANGE);
    }

    @Test
    @DisplayName("매장이 아니면 영업시간을 저장할 수 없다")
    void updateBusinessHours_customer_throwsForbidden() {
        assertThatThrownBy(() -> service.updateBusinessHours(customer, new BusinessHourSaveRequest(week(10, 20))))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("영업시간을 줄여 예약이 영업시간 밖으로 밀려나면 차단한다")
    void updateBusinessHours_strandsReservation_throwsReservedTime() {
        given(shopBusinessHourRepository.save(any(ShopBusinessHour.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(confirmedReservation(SUNDAY_14)));

        assertThatThrownBy(() -> service.updateBusinessHours(owner, new BusinessHourSaveRequest(week(10, 14))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_RESERVED_TIME);
    }

    @Test
    @DisplayName("줄어든 시간이어도 시간대 열기가 예약을 덮으면 저장한다")
    void updateBusinessHours_reservationCoveredByOpen_saves() {
        given(shopBusinessHourRepository.save(any(ShopBusinessHour.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(confirmedReservation(SUNDAY_14)));
        given(shopScheduleExceptionRepository.findOverlapping(any(), any(), any()))
                .willReturn(List.of(ShopScheduleException.builder()
                        .shopId(owner.shopId())
                        .exceptionType(ScheduleExceptionType.OPEN)
                        .startsAt(SUNDAY_14)
                        .endsAt(SUNDAY_14.plusHours(2))
                        .build()));

        List<BusinessHourResponse> result =
                service.updateBusinessHours(owner, new BusinessHourSaveRequest(week(10, 14)));

        assertThat(result).hasSize(7);
    }

    @Test
    @DisplayName("줄어든 시간에 밀려나는 예약이 있으면 어느 예약인지 알려준다")
    void updateBusinessHours_strandsReservation_namesReservation() {
        given(shopBusinessHourRepository.save(any(ShopBusinessHour.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(confirmedReservation(SUNDAY_14)));

        assertThatThrownBy(() -> service.updateBusinessHours(owner, new BusinessHourSaveRequest(week(10, 14))))
                .hasMessageContaining("10/25(일) 14:00");
    }

    @Test
    @DisplayName("앞으로 예약이 있는 요일도 휴무로 바꿀 수 있고 예약은 그대로 둔다")
    void updateBusinessHours_closeDayWithReservation_saves() {
        ShopBusinessHour sunday = hour(BusinessDay.SUN, 10, 20);
        given(shopBusinessHourRepository.findByShopId(owner.shopId())).willReturn(List.of(sunday));
        given(shopBusinessHourRepository.save(any(ShopBusinessHour.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        Reservation reservation = confirmedReservation(SUNDAY_14);
        given(reservationRepository.findActiveOverlapping(any(), any(), any())).willReturn(List.of(reservation));

        List<BusinessHourResponse> result = service.updateBusinessHours(owner, closeSunday(null));

        assertThat(sunday.isClosed()).isTrue();
        assertThat(result).hasSize(7);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(shopScheduleExceptionRepository, never()).delete(any(ShopScheduleException.class));
    }

    @Test
    @DisplayName("휴무로 바꾸며 열기를 함께 닫으면 예약 없는 열기만 지운다")
    void updateBusinessHours_closeOpenExceptions_deletesOnlyUnreserved() {
        given(shopBusinessHourRepository.findByShopId(owner.shopId()))
                .willReturn(List.of(hour(BusinessDay.SUN, 10, 20)));
        given(shopBusinessHourRepository.save(any(ShopBusinessHour.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(confirmedReservation(SUNDAY_14)));
        ShopScheduleException reserved = open(SUNDAY_14);
        ShopScheduleException free = open(SUNDAY_14.plusHours(4));
        ShopScheduleException otherDay = open(SUNDAY_14.plusDays(1));
        given(shopScheduleExceptionRepository.findOverlapping(any(), any(), any()))
                .willReturn(List.of(reserved, free, otherDay));

        service.updateBusinessHours(owner, closeSunday(true));

        verify(shopScheduleExceptionRepository).delete(free);
        verify(shopScheduleExceptionRepository, never()).delete(reserved);
        verify(shopScheduleExceptionRepository, never()).delete(otherDay);
    }

    @Test
    @DisplayName("원래 휴무였던 요일에 연 시간은 함께 닫기로 지우지 않는다")
    void updateBusinessHours_alreadyClosedDay_keepsOpenExceptions() {
        ShopBusinessHour sunday = ShopBusinessHour.builder().shopId(owner.shopId()).dayOfWeek(BusinessDay.SUN)
                .closed(true).build();
        given(shopBusinessHourRepository.findByShopId(owner.shopId())).willReturn(List.of(sunday));
        given(shopBusinessHourRepository.save(any(ShopBusinessHour.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.updateBusinessHours(owner, closeSunday(true));

        verify(shopScheduleExceptionRepository, never()).delete(any(ShopScheduleException.class));
    }

    @Test
    @DisplayName("휴무 전환 영향은 그 요일의 예약과 열기만 돌려주고 예약 있는 열기를 표시한다")
    void getClosingImpact_returnsReservationsAndOpensOnClosingDays() {
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(confirmedReservation(SUNDAY_14), confirmedReservation(SUNDAY_14.plusDays(1))));
        given(shopScheduleExceptionRepository.findOverlapping(any(), any(), any()))
                .willReturn(List.of(open(SUNDAY_14), open(SUNDAY_14.plusHours(4)), open(SUNDAY_14.plusDays(1))));

        BusinessHourClosingImpactResponse impact = service.getClosingImpact(owner, List.of(BusinessDay.SUN));

        assertThat(impact.reservations()).hasSize(1);
        assertThat(impact.openExceptions()).extracting(BusinessHourClosingImpactResponse.OpenException::closable)
                .containsExactly(false, true);
    }

    @Test
    @DisplayName("다른 요일까지 이어진 열기는 영향에 보여주되 함께 닫을 수 없다")
    void getClosingImpact_multiDayOpen_isNotClosable() {
        // 토요일 22시 ~ 일요일 2시. 일요일만 휴무로 바꾼다
        ShopScheduleException overnight = open(LocalDateTime.of(2026, 10, 24, 22, 0), LocalDateTime.of(2026, 10, 25, 2, 0));
        given(shopScheduleExceptionRepository.findOverlapping(any(), any(), any())).willReturn(List.of(overnight));

        BusinessHourClosingImpactResponse impact = service.getClosingImpact(owner, List.of(BusinessDay.SUN));

        assertThat(impact.openExceptions()).extracting(BusinessHourClosingImpactResponse.OpenException::closable)
                .containsExactly(false);
    }

    @Test
    @DisplayName("다음 날 0시에 끝나는 열기는 그 요일 하루의 열기로 본다")
    void getClosingImpact_openEndingAtMidnight_isClosable() {
        ShopScheduleException untilMidnight = open(LocalDateTime.of(2026, 10, 25, 22, 0), LocalDateTime.of(2026, 10, 26, 0, 0));
        given(shopScheduleExceptionRepository.findOverlapping(any(), any(), any())).willReturn(List.of(untilMidnight));

        BusinessHourClosingImpactResponse impact = service.getClosingImpact(owner, List.of(BusinessDay.SUN));

        assertThat(impact.openExceptions()).extracting(BusinessHourClosingImpactResponse.OpenException::closable)
                .containsExactly(true);
    }

    @Test
    @DisplayName("다른 요일까지 이어진 열기는 함께 닫기로 지우지 않는다")
    void updateBusinessHours_multiDayOpen_isKept() {
        given(shopBusinessHourRepository.findByShopId(owner.shopId()))
                .willReturn(List.of(hour(BusinessDay.SUN, 10, 20)));
        given(shopBusinessHourRepository.save(any(ShopBusinessHour.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(shopScheduleExceptionRepository.findOverlapping(any(), any(), any()))
                .willReturn(List.of(open(LocalDateTime.of(2026, 10, 24, 22, 0), LocalDateTime.of(2026, 10, 25, 2, 0))));

        service.updateBusinessHours(owner, closeSunday(true));

        verify(shopScheduleExceptionRepository, never()).delete(any(ShopScheduleException.class));
    }

    @Test
    @DisplayName("고객은 휴무 전환 영향을 조회할 수 없다")
    void getClosingImpact_customer_throwsForbidden() {
        assertThatThrownBy(() -> service.getClosingImpact(customer, List.of(BusinessDay.SUN)))
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    private ShopScheduleException open(LocalDateTime startsAt) {
        return open(startsAt, startsAt.plusHours(2));
    }

    private ShopScheduleException open(LocalDateTime startsAt, LocalDateTime endsAt) {
        return ShopScheduleException.builder()
                .shopId(owner.shopId())
                .exceptionType(ScheduleExceptionType.OPEN)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
    }

    private static BusinessHourSaveRequest closeSunday(Boolean closeOpenExceptions) {
        List<BusinessHourSaveRequest.Day> days = week(10, 20);
        days.replaceAll(day -> day.dayOfWeek() == BusinessDay.SUN
                ? new BusinessHourSaveRequest.Day(BusinessDay.SUN, true, null, null) : day);
        return new BusinessHourSaveRequest(days, closeOpenExceptions);
    }

    private static List<BusinessHourSaveRequest.Day> week(int openHour, int closeHour) {
        List<BusinessHourSaveRequest.Day> days = new ArrayList<>();
        for (BusinessDay day : BusinessDay.values()) {
            days.add(new BusinessHourSaveRequest.Day(
                    day, false, LocalTime.of(openHour, 0), LocalTime.of(closeHour, 0)));
        }
        return days;
    }

    private static BusinessHourSaveRequest requestWithMonday(LocalTime openTime, LocalTime closeTime) {
        List<BusinessHourSaveRequest.Day> days = week(10, 20);
        days.set(0, new BusinessHourSaveRequest.Day(BusinessDay.MON, false, openTime, closeTime));
        return new BusinessHourSaveRequest(days);
    }

    private ShopBusinessHour hour(BusinessDay day, int openHour, int closeHour) {
        return ShopBusinessHour.builder()
                .shopId(owner.shopId())
                .dayOfWeek(day)
                .closed(false)
                .openTime(LocalTime.of(openHour, 0))
                .closeTime(LocalTime.of(closeHour, 0))
                .build();
    }

    private static Reservation confirmedReservation(LocalDateTime startsAt) {
        Reservation reservation = Reservation.builder()
                .userId(1L)
                .treatmentId(1L)
                .startsAt(startsAt)
                .endsAt(startsAt.plusHours(1))
                .appliedPrice(20_000)
                .deposit(20_000)
                .holdExpiresAt(NOW)
                .build();
        // 상태 변경 메서드는 예약 담당(D)이 추가하므로 테스트에서는 직접 넣는다
        ReflectionTestUtils.setField(reservation, "status", ReservationStatus.CONFIRMED);
        return reservation;
    }
}
