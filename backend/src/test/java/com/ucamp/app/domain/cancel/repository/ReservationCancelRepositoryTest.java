package com.ucamp.app.domain.cancel.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.cancel.CancelFixture;
import com.ucamp.app.domain.cancel.entity.CancelStatus;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.cancel.entity.ReservationCancel;

// 스키마는 docs/erd/01_schema_ddl.sql 로 만든 test 프로필 H2(MODE=Oracle)를 그대로 쓴다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig.class)
@ActiveProfiles("test")
class ReservationCancelRepositoryTest {

    private static final long RESERVATION_ID = 100L;
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Autowired
    private ReservationCancelRepository reservationCancelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        CancelFixture.insertBase(jdbcTemplate);
        CancelFixture.insertConfirmedReservation(jdbcTemplate, RESERVATION_ID, CancelFixture.CUSTOMER_ID, STARTS_AT);
    }

    @Test
    @DisplayName("빌더로 만든 취소는 REQUESTED 상태로 시퀀스 ID 를 받아 저장되고 예약 ID 로 조회된다")
    void save_newCancel_isRequested() {
        ReservationCancel saved = reservationCancelRepository.saveAndFlush(newCancel());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(CancelStatus.REQUESTED);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(reservationCancelRepository.findByReservationId(RESERVATION_ID))
                .get().extracting(ReservationCancel::getId).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("같은 예약의 두 번째 취소는 UNIQUE 제약으로 저장되지 않는다")
    void save_duplicateReservation_violatesUnique() {
        reservationCancelRepository.saveAndFlush(newCancel());

        assertThatThrownBy(() -> reservationCancelRepository.saveAndFlush(newCancel()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("결제 취소에 실패한 취소는 같은 행을 갱신해 다시 접수한다")
    void retry_failedCancel_updatesSameRow() {
        ReservationCancel cancel = reservationCancelRepository.saveAndFlush(newCancel());
        cancel.fail();
        reservationCancelRepository.flush();

        cancel.retry(CancelType.CUSTOMER, 10_000, 10_000, REQUESTED_AT.plusDays(1));
        reservationCancelRepository.flush();

        ReservationCancel found = reservationCancelRepository.findByReservationId(RESERVATION_ID).orElseThrow();
        assertThat(found.getId()).isEqualTo(cancel.getId());
        assertThat(found.getStatus()).isEqualTo(CancelStatus.REQUESTED);
        assertThat(found.getRefundAmount()).isEqualTo(10_000);
        assertThat(reservationCancelRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("결제 취소 성공 시 완료 시각과 PG 취소 키를 저장한다")
    void complete_storesPgKeyAndCompletedAt() {
        ReservationCancel cancel = reservationCancelRepository.saveAndFlush(newCancel());

        cancel.complete("pg-cancel-1", REQUESTED_AT.plusSeconds(3));
        reservationCancelRepository.flush();

        ReservationCancel found = reservationCancelRepository.findById(cancel.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(CancelStatus.COMPLETED);
        assertThat(found.getPgCancelKey()).isEqualTo("pg-cancel-1");
        assertThat(found.getCompletedAt()).isEqualTo(REQUESTED_AT.plusSeconds(3));
    }

    @Test
    @DisplayName("완료된 취소는 실패·재시도로 바꿀 수 없다")
    void completedCancel_cannotFailOrRetry() {
        ReservationCancel cancel = newCancel();
        cancel.complete("pg-cancel-1", REQUESTED_AT);

        assertThatThrownBy(cancel::fail).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cancel.retry(CancelType.CUSTOMER, 0, 0, REQUESTED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    private ReservationCancel newCancel() {
        return ReservationCancel.builder()
                .reservationId(RESERVATION_ID)
                .userId(CancelFixture.CUSTOMER_ID)
                .cancelType(CancelType.CUSTOMER)
                .policyRefundAmount(20_000)
                .refundAmount(20_000)
                .requestedAt(REQUESTED_AT)
                .build();
    }
}
