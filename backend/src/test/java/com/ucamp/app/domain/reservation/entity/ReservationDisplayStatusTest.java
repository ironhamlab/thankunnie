package com.ucamp.app.domain.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReservationDisplayStatusTest {

    private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 11, 3, 18, 0);

    @Test
    @DisplayName("확정 예약은 종료 시각 전까지 예약 확정이다")
    void of_confirmedBeforeEnd_returnsConfirmed() {
        assertThat(ReservationDisplayStatus.of(ReservationStatus.CONFIRMED, ENDS_AT, false, ENDS_AT.minusNanos(1)))
                .isEqualTo(ReservationDisplayStatus.CONFIRMED);
    }

    @Test
    @DisplayName("확정 예약은 종료 시각이 되는 순간부터 이용 완료다")
    void of_confirmedAtEnd_returnsCompleted() {
        assertThat(ReservationDisplayStatus.of(ReservationStatus.CONFIRMED, ENDS_AT, false, ENDS_AT))
                .isEqualTo(ReservationDisplayStatus.COMPLETED);
    }

    @Test
    @DisplayName("노쇼로 표시한 확정 예약은 이용 완료가 아니라 노쇼다")
    void of_noShow_returnsNoShow() {
        assertThat(ReservationDisplayStatus.of(ReservationStatus.CONFIRMED, ENDS_AT, true, ENDS_AT.plusHours(1)))
                .isEqualTo(ReservationDisplayStatus.NO_SHOW);
    }

    @ParameterizedTest
    @CsvSource({"HOLD,HOLD", "CANCEL_PENDING,CANCEL_PENDING", "CANCELLED,CANCELLED", "EXPIRED,EXPIRED"})
    @DisplayName("확정이 아닌 예약은 종료 시각이 지나도 저장 상태 그대로다")
    void of_notConfirmed_keepsStatus(ReservationStatus status, ReservationDisplayStatus expected) {
        assertThat(ReservationDisplayStatus.of(status, ENDS_AT, false, ENDS_AT.plusDays(1))).isEqualTo(expected);
    }
}
