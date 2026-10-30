package com.ucamp.app.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.payment.entity.DepositPayment;
import com.ucamp.app.domain.payment.entity.DepositPaymentStatus;
import com.ucamp.app.domain.payment.repository.DepositPaymentRepository;
import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.service.ReservationService;

class PaymentConfirmServiceTest {

    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 10, 29, 14, 58, 20);

    private DepositPaymentRepository depositPaymentRepository;
    private ReservationService reservationService;
    private PaymentConfirmService confirmService;

    @BeforeEach
    void setUp() {
        depositPaymentRepository = mock(DepositPaymentRepository.class);
        reservationService = mock(ReservationService.class);
        confirmService = new PaymentConfirmService(depositPaymentRepository, reservationService);
    }

    private Reservation reservation(long id) {
        Reservation reservation = mock(Reservation.class);
        given(reservation.getId()).willReturn(id);
        return reservation;
    }

    private DepositPayment savedPayment(long reservationId) {
        DepositPayment payment = mock(DepositPayment.class);
        Reservation reservation = reservation(reservationId);
        given(payment.getReservation()).willReturn(reservation);
        given(payment.getDepositPaymentId()).willReturn(7L);
        return payment;
    }

    @Test
    @DisplayName("예약을 확정한 뒤 예약금결제를 PAID·결제 금액·포트원 고유번호·결제 시각으로 저장한다")
    void confirm_newPayment_confirmsAndSaves() {
        Reservation reservation = reservation(10L);
        given(depositPaymentRepository.findByPgTransactionKey("imp_123")).willReturn(Optional.empty());
        given(reservationService.confirmReservation(1L, 10L, 20000, "요청")).willReturn(reservation);
        given(depositPaymentRepository.save(any(DepositPayment.class))).willAnswer(call -> call.getArgument(0));

        confirmService.confirm(1L, 10L, "imp_123", 20000, "요청", APPROVED_AT);

        ArgumentCaptor<DepositPayment> captor = ArgumentCaptor.forClass(DepositPayment.class);
        verify(depositPaymentRepository).save(captor.capture());
        DepositPayment saved = captor.getValue();
        assertThat(saved.getReservation()).isSameAs(reservation);
        assertThat(saved.getStatus()).isEqualTo(DepositPaymentStatus.PAID);
        assertThat(saved.getAmount()).isEqualTo(20000);
        assertThat(saved.getPgProvider()).isEqualTo("PORTONE");
        assertThat(saved.getPgTransactionKey()).isEqualTo("imp_123");
        assertThat(saved.getApprovedAt()).isEqualTo(APPROVED_AT);
    }

    @Test
    @DisplayName("같은 결제가 이 예약으로 이미 저장돼 있으면 다시 확정하지 않고 저장된 결제 ID 를 돌려준다")
    void confirm_alreadySaved_returnsSavedId() {
        DepositPayment payment = savedPayment(10L);
        given(depositPaymentRepository.findByPgTransactionKey("imp_123")).willReturn(Optional.of(payment));

        Long id = confirmService.confirm(1L, 10L, "imp_123", 20000, null, APPROVED_AT);

        assertThat(id).isEqualTo(7L);
        verify(reservationService, never()).confirmReservation(anyLong(), anyLong(), anyInt(), anyString());
        verify(depositPaymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 결제가 다른 예약에 저장돼 있으면 PAYMENT_007 이다")
    void confirm_savedForOtherReservation_throws() {
        DepositPayment payment = savedPayment(99L);
        given(depositPaymentRepository.findByPgTransactionKey("imp_123")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> confirmService.confirm(1L, 10L, "imp_123", 20000, null, APPROVED_AT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_RESERVATION_MISMATCH);
    }

    @Test
    @DisplayName("예약을 확정하지 못하면 예약금결제를 저장하지 않는다")
    void confirm_reservationFails_doesNotSave() {
        given(depositPaymentRepository.findByPgTransactionKey("imp_123")).willReturn(Optional.empty());
        given(reservationService.confirmReservation(1L, 10L, 20000, null))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_HOLD_EXPIRED));

        assertThatThrownBy(() -> confirmService.confirm(1L, 10L, "imp_123", 20000, null, APPROVED_AT))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_HOLD_EXPIRED);
        verify(depositPaymentRepository, never()).save(any());
    }
}
