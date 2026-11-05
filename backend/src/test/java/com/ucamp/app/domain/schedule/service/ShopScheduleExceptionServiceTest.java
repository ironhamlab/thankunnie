package com.ucamp.app.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.TestUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.ShopLock;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.schedule.dto.request.ScheduleExceptionSaveRequest;
import com.ucamp.app.domain.schedule.dto.response.ScheduleExceptionResponse;
import com.ucamp.app.domain.schedule.entity.ScheduleExceptionType;
import com.ucamp.app.domain.schedule.entity.ShopScheduleException;
import com.ucamp.app.domain.schedule.repository.ShopScheduleExceptionRepository;

@ExtendWith(MockitoExtension.class)
class ShopScheduleExceptionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 9, 0);
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final AuthUser owner = TestUser.OWNER_1.toAuthUser();
    private final AuthUser otherOwner = TestUser.OWNER_2.toAuthUser();
    private final AuthUser customer = TestUser.CUSTOMER_1.toAuthUser();

    @Mock
    private ShopScheduleExceptionRepository shopScheduleExceptionRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ShopLock shopLock;

    private ShopScheduleExceptionService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(BookingPolicy.ZONE).toInstant(), BookingPolicy.ZONE);
        service = new ShopScheduleExceptionService(
                shopScheduleExceptionRepository, reservationRepository, shopLock, fixedClock);
    }

    @Test
    @DisplayName("매장이 닫기를 등록하면 본인 매장으로 저장하고 +09:00 시각으로 응답한다")
    void createException_owner_savesWithOwnShop() {
        given(shopScheduleExceptionRepository.save(any(ShopScheduleException.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ScheduleExceptionResponse response = service.createException(owner, closeRequest(12, 13));

        assertThat(response.exceptionType()).isEqualTo(ScheduleExceptionType.CLOSE);
        assertThat(response.startsAt()).isEqualTo(OffsetDateTime.of(2026, 10, 25, 12, 0, 0, 0, KST));
        verify(shopScheduleExceptionRepository).save(any(ShopScheduleException.class));
    }

    @Test
    @DisplayName("다른 오프셋으로 요청해도 같은 순간의 한국 시각으로 저장한다")
    void createException_utcRequest_convertsToKoreaTime() {
        given(shopScheduleExceptionRepository.save(any(ShopScheduleException.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        // 2026-10-25 03:00Z = 12:00 KST
        ScheduleExceptionSaveRequest request = new ScheduleExceptionSaveRequest(ScheduleExceptionType.CLOSE,
                OffsetDateTime.of(2026, 10, 25, 3, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 10, 25, 4, 0, 0, 0, ZoneOffset.UTC), null);

        ScheduleExceptionResponse response = service.createException(owner, request);

        assertThat(response.startsAt()).isEqualTo(OffsetDateTime.of(2026, 10, 25, 12, 0, 0, 0, KST));
    }

    @Test
    @DisplayName("매장이 아니면 등록할 수 없다")
    void createException_customer_throwsForbidden() {
        assertThatThrownBy(() -> service.createException(customer, closeRequest(12, 13)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(shopScheduleExceptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("시각 검사를 통과하지 못하면 저장하지 않는다")
    void createException_invalidTime_doesNotSave() {
        ScheduleExceptionSaveRequest request = new ScheduleExceptionSaveRequest(ScheduleExceptionType.CLOSE,
                OffsetDateTime.of(2026, 10, 25, 12, 15, 0, 0, KST),
                OffsetDateTime.of(2026, 10, 25, 13, 0, 0, 0, KST), null);

        assertThatThrownBy(() -> service.createException(owner, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_TIME_UNIT);
        verify(shopScheduleExceptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 종류의 일정과 시간이 겹치면 등록하지 않는다")
    void createException_sameTypeOverlap_throwsOverlap() {
        given(shopScheduleExceptionRepository.existsSameTypeOverlapping(owner.shopId(),
                ScheduleExceptionType.OPEN, LocalDateTime.of(2026, 10, 25, 14, 0),
                LocalDateTime.of(2026, 10, 25, 16, 0), null)).willReturn(true);

        assertThatThrownBy(() -> service.createException(owner, openRequest(14, 16)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_SCHEDULE_EXCEPTION_OVERLAP);
        verify(shopScheduleExceptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("겹침 검사는 매장을 잠근 뒤에 해서 연달아 눌러도 한 건만 저장된다")
    void createException_checksOverlapAfterLock() {
        given(shopScheduleExceptionRepository.save(any(ShopScheduleException.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.createException(owner, closeRequest(12, 13));

        InOrder inOrder = inOrder(shopLock, shopScheduleExceptionRepository);
        inOrder.verify(shopLock).lock(owner.shopId());
        inOrder.verify(shopScheduleExceptionRepository).existsSameTypeOverlapping(owner.shopId(),
                ScheduleExceptionType.CLOSE, LocalDateTime.of(2026, 10, 25, 12, 0),
                LocalDateTime.of(2026, 10, 25, 13, 0), null);
    }

    @Test
    @DisplayName("수정은 자기 자신을 뺀 같은 종류 일정과 겹치면 막는다")
    void updateException_sameTypeOverlapExceptItself_throwsOverlap() {
        given(shopScheduleExceptionRepository.findById(1L)).willReturn(Optional.of(existing(owner.shopId())));
        given(shopScheduleExceptionRepository.existsSameTypeOverlapping(owner.shopId(),
                ScheduleExceptionType.CLOSE, LocalDateTime.of(2026, 10, 25, 14, 0),
                LocalDateTime.of(2026, 10, 25, 16, 0), 1L)).willReturn(true);

        assertThatThrownBy(() -> service.updateException(owner, 1L, closeRequest(14, 16)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_SCHEDULE_EXCEPTION_OVERLAP);
    }

    @Test
    @DisplayName("본인 매장 일정은 수정한 값으로 응답한다")
    void updateException_ownShop_changesValues() {
        given(shopScheduleExceptionRepository.findById(1L)).willReturn(Optional.of(existing(owner.shopId())));

        ScheduleExceptionResponse response = service.updateException(owner, 1L, closeRequest(14, 16));

        assertThat(response.startsAt().getHour()).isEqualTo(14);
        assertThat(response.endsAt().getHour()).isEqualTo(16);
    }

    @Test
    @DisplayName("다른 매장 일정은 수정할 수 없다")
    void updateException_otherShop_throwsForbidden() {
        given(shopScheduleExceptionRepository.findById(1L)).willReturn(Optional.of(existing(owner.shopId())));

        assertThatThrownBy(() -> service.updateException(otherOwner, 1L, closeRequest(14, 16)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("없는 일정을 수정하면 NOT_FOUND 예외가 발생한다")
    void updateException_notFound_throwsNotFound() {
        given(shopScheduleExceptionRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateException(owner, 99L, closeRequest(14, 16)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_SCHEDULE_EXCEPTION_NOT_FOUND);
    }

    @Test
    @DisplayName("수정은 매장을 잠근 뒤 일정을 조회해 동시 변경된 유형을 기준으로 검사한다")
    void updateException_locksShopBeforeLoadingException() {
        given(shopScheduleExceptionRepository.findById(1L)).willReturn(Optional.of(existing(owner.shopId())));

        service.updateException(owner, 1L, closeRequest(14, 16));

        InOrder inOrder = inOrder(shopLock, shopScheduleExceptionRepository);
        inOrder.verify(shopLock).lock(owner.shopId());
        inOrder.verify(shopScheduleExceptionRepository).findById(1L);
    }

    @Test
    @DisplayName("삭제는 매장을 잠근 뒤 일정을 조회한다")
    void deleteException_locksShopBeforeLoadingException() {
        given(shopScheduleExceptionRepository.findById(1L)).willReturn(Optional.of(existing(owner.shopId())));

        service.deleteException(owner, 1L);

        InOrder inOrder = inOrder(shopLock, shopScheduleExceptionRepository);
        inOrder.verify(shopLock).lock(owner.shopId());
        inOrder.verify(shopScheduleExceptionRepository).findById(1L);
    }

    @Test
    @DisplayName("매장이 아니면 삭제할 수 없고 매장을 잠그지 않는다")
    void deleteException_customer_throwsForbidden() {
        assertThatThrownBy(() -> service.deleteException(customer, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verify(shopLock, never()).lock(any());
    }

    @Test
    @DisplayName("본인 매장 일정은 삭제한다")
    void deleteException_ownShop_deletes() {
        ShopScheduleException exception = existing(owner.shopId());
        given(shopScheduleExceptionRepository.findById(1L)).willReturn(Optional.of(exception));

        service.deleteException(owner, 1L);

        verify(shopScheduleExceptionRepository).delete(exception);
    }

    @Test
    @DisplayName("조회 종료일이 시작일보다 앞이면 예외가 발생한다")
    void getExceptions_toBeforeFrom_throwsInvalidRange() {
        LocalDate from = LocalDate.of(2026, 10, 25);

        assertThatThrownBy(() -> service.getExceptions(owner, from, from.minusDays(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_TIME_RANGE);
    }

    @Test
    @DisplayName("확정 예약이 있는 시간은 닫을 수 없고 저장하지 않는다")
    void createException_closeOverConfirmedReservation_throwsReservedTime() {
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(reservation(12, 13, ReservationStatus.CONFIRMED, NOW)));

        assertThatThrownBy(() -> service.createException(owner, closeRequest(12, 14)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_RESERVED_TIME);
        verify(shopLock).lock(owner.shopId());
        verify(shopScheduleExceptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("예약 시간을 빼고 닫으면 예약 앞뒤 구간만 닫기로 저장한다")
    void createException_excludeReserved_savesAroundReservations() {
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(reservation(14, 15, ReservationStatus.CONFIRMED, NOW)));
        given(shopScheduleExceptionRepository.save(any(ShopScheduleException.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ScheduleExceptionResponse response = service.createException(owner, excludeReservedRequest(10, 20));

        ArgumentCaptor<ShopScheduleException> saved = ArgumentCaptor.forClass(ShopScheduleException.class);
        verify(shopScheduleExceptionRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ShopScheduleException::getStartsAt)
                .containsExactly(day(10), day(15));
        assertThat(saved.getAllValues()).extracting(ShopScheduleException::getEndsAt)
                .containsExactly(day(14), day(20));
        assertThat(response.startsAt()).isEqualTo(OffsetDateTime.of(day(10), KST));
    }

    @Test
    @DisplayName("예약 시간을 빼고 닫아도 만료된 점유는 예약으로 보지 않는다")
    void createException_excludeReservedExpiredHold_savesWholeRange() {
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(reservation(14, 15, ReservationStatus.HOLD, NOW.minusMinutes(1))));
        given(shopScheduleExceptionRepository.save(any(ShopScheduleException.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ScheduleExceptionResponse response = service.createException(owner, excludeReservedRequest(10, 20));

        verify(shopScheduleExceptionRepository).save(any(ShopScheduleException.class));
        assertThat(response.endsAt()).isEqualTo(OffsetDateTime.of(day(20), KST));
    }

    @Test
    @DisplayName("전 구간에 예약이 있으면 예약 시간을 빼고 닫을 수 없다")
    void createException_excludeReservedFullyBooked_throwsReservedTime() {
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(reservation(12, 14, ReservationStatus.CONFIRMED, NOW)));

        assertThatThrownBy(() -> service.createException(owner, excludeReservedRequest(12, 14)))
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_RESERVED_TIME);
        verify(shopScheduleExceptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("점유가 만료된 예약만 있으면 닫을 수 있다")
    void createException_closeOverExpiredHold_saves() {
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(reservation(12, 13, ReservationStatus.HOLD, NOW.minusMinutes(1))));
        given(shopScheduleExceptionRepository.save(any(ShopScheduleException.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.createException(owner, closeRequest(12, 14));

        verify(shopScheduleExceptionRepository).save(any(ShopScheduleException.class));
    }

    @Test
    @DisplayName("점유 중인 예약이 있는 열기는 삭제할 수 없다")
    void deleteException_openWithActiveHold_throwsReservedTime() {
        given(shopScheduleExceptionRepository.findById(1L))
                .willReturn(Optional.of(existing(owner.shopId(), ScheduleExceptionType.OPEN, 18, 20)));
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(reservation(18, 19, ReservationStatus.HOLD, NOW.plusMinutes(3))));

        assertThatThrownBy(() -> service.deleteException(owner, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_RESERVED_TIME);
        verify(shopScheduleExceptionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("닫기 삭제는 예약과 관계없이 할 수 있다")
    void deleteException_close_deletesWithoutReservationCheck() {
        ShopScheduleException close = existing(owner.shopId());
        given(shopScheduleExceptionRepository.findById(1L)).willReturn(Optional.of(close));

        service.deleteException(owner, 1L);

        verify(reservationRepository, never()).findActiveOverlapping(any(), any(), any());
        verify(shopScheduleExceptionRepository).delete(close);
    }

    @Test
    @DisplayName("열기를 줄여 예약이 열기 구간 밖으로 밀려나면 차단한다")
    void updateException_shrinkOpenOutsideReservation_throwsReservedTime() {
        given(shopScheduleExceptionRepository.findById(1L))
                .willReturn(Optional.of(existing(owner.shopId(), ScheduleExceptionType.OPEN, 18, 22)));
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(reservation(20, 21, ReservationStatus.CONFIRMED, NOW)));

        assertThatThrownBy(() -> service.updateException(owner, 1L, openRequest(18, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_RESERVED_TIME);
    }

    @Test
    @DisplayName("열기를 줄여도 예약이 새 열기 구간 안에 있으면 수정한다")
    void updateException_shrinkOpenKeepingReservation_changes() {
        given(shopScheduleExceptionRepository.findById(1L))
                .willReturn(Optional.of(existing(owner.shopId(), ScheduleExceptionType.OPEN, 18, 22)));
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(reservation(18, 19, ReservationStatus.CONFIRMED, NOW)));

        ScheduleExceptionResponse response = service.updateException(owner, 1L, openRequest(18, 20));

        assertThat(response.endsAt().getHour()).isEqualTo(20);
    }

    private static ScheduleExceptionSaveRequest openRequest(int startHour, int endHour) {
        return new ScheduleExceptionSaveRequest(ScheduleExceptionType.OPEN,
                OffsetDateTime.of(2026, 10, 25, startHour, 0, 0, 0, KST),
                OffsetDateTime.of(2026, 10, 25, endHour, 0, 0, 0, KST),
                "연장 영업");
    }

    private static Reservation reservation(int startHour, int endHour, ReservationStatus status,
            LocalDateTime holdExpiresAt) {
        Reservation reservation = Reservation.builder()
                .userId(1L)
                .treatmentId(1L)
                .startsAt(LocalDateTime.of(2026, 10, 25, startHour, 0))
                .endsAt(LocalDateTime.of(2026, 10, 25, endHour, 0))
                .appliedPrice(20_000)
                .deposit(20_000)
                .holdExpiresAt(holdExpiresAt)
                .build();
        // 상태 변경 메서드는 예약 담당(D)이 추가하므로 테스트에서는 직접 넣는다
        ReflectionTestUtils.setField(reservation, "status", status);
        return reservation;
    }

    private static LocalDateTime day(int hour) {
        return LocalDateTime.of(2026, 10, 25, hour, 0);
    }

    private static ScheduleExceptionSaveRequest excludeReservedRequest(int startHour, int endHour) {
        return new ScheduleExceptionSaveRequest(ScheduleExceptionType.CLOSE,
                OffsetDateTime.of(day(startHour), KST), OffsetDateTime.of(day(endHour), KST), "임시 휴무", true);
    }

    private static ScheduleExceptionSaveRequest closeRequest(int startHour, int endHour) {
        return new ScheduleExceptionSaveRequest(ScheduleExceptionType.CLOSE,
                OffsetDateTime.of(2026, 10, 25, startHour, 0, 0, 0, KST),
                OffsetDateTime.of(2026, 10, 25, endHour, 0, 0, 0, KST),
                "점심시간");
    }

    private static ShopScheduleException existing(Long shopId) {
        return existing(shopId, ScheduleExceptionType.CLOSE, 12, 13);
    }

    private static ShopScheduleException existing(Long shopId, ScheduleExceptionType type, int startHour,
            int endHour) {
        return ShopScheduleException.builder()
                .shopId(shopId)
                .exceptionType(type)
                .startsAt(LocalDateTime.of(2026, 10, 25, startHour, 0))
                .endsAt(LocalDateTime.of(2026, 10, 25, endHour, 0))
                .build();
    }
}
