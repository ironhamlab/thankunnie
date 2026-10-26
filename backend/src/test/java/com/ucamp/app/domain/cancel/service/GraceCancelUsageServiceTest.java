package com.ucamp.app.domain.cancel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.cancel.CancelFixture;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.cancel.entity.ReservationCancel;
import com.ucamp.app.domain.cancel.repository.ReservationCancelRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TimeConfig.class, GraceCancelUsageService.class})
@ActiveProfiles("test")
class GraceCancelUsageServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Autowired
    private GraceCancelUsageService graceCancelUsageService;

    @Autowired
    private ReservationCancelRepository reservationCancelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long firstCancelId;
    private long secondCancelId;

    @BeforeEach
    void setUp() {
        CancelFixture.insertBase(jdbcTemplate);
        CancelFixture.insertConfirmedReservation(jdbcTemplate, 100L, CancelFixture.CUSTOMER_ID, STARTS_AT);
        CancelFixture.insertConfirmedReservation(jdbcTemplate, 101L, CancelFixture.CUSTOMER_ID, STARTS_AT.plusHours(2));
        firstCancelId = saveCancel(100L);
        secondCancelId = saveCancel(101L);
    }

    @Test
    @DisplayName("무료 취소를 기록하면 그날은 사용한 것으로 조회된다")
    void record_thenUsedToday() {
        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, TODAY)).isFalse();

        graceCancelUsageService.record(CancelFixture.CUSTOMER_ID, TODAY, firstCancelId);

        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, TODAY)).isTrue();
        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, TODAY.plusDays(1))).isFalse();
        assertThat(graceCancelUsageService.isUsed(CancelFixture.OTHER_CUSTOMER_ID, TODAY)).isFalse();
    }

    @Test
    @DisplayName("같은 날 두 번째 무료 취소는 덮어쓰지 않고 오늘 이미 사용 예외가 발생한다")
    void record_secondSameDay_alreadyUsed() {
        graceCancelUsageService.record(CancelFixture.CUSTOMER_ID, TODAY, firstCancelId);

        assertThatThrownBy(() -> graceCancelUsageService.record(CancelFixture.CUSTOMER_ID, TODAY, secondCancelId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CANCEL_GRACE_ALREADY_USED);
    }

    @Test
    @DisplayName("다른 날에는 다시 무료 취소를 기록할 수 있다")
    void record_nextDay_allowed() {
        graceCancelUsageService.record(CancelFixture.CUSTOMER_ID, TODAY, firstCancelId);
        graceCancelUsageService.record(CancelFixture.CUSTOMER_ID, TODAY.plusDays(1), secondCancelId);

        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, TODAY.plusDays(1))).isTrue();
    }

    @Test
    @DisplayName("결제 취소 실패로 기록을 지우면 같은 날 다시 무료 취소를 쓸 수 있다")
    void release_thenAvailableAgain() {
        graceCancelUsageService.record(CancelFixture.CUSTOMER_ID, TODAY, firstCancelId);

        graceCancelUsageService.release(firstCancelId);

        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, TODAY)).isFalse();
        graceCancelUsageService.record(CancelFixture.CUSTOMER_ID, TODAY, secondCancelId);
        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, TODAY)).isTrue();
    }

    @Test
    @DisplayName("기록이 없는 취소를 지워도 오류가 나지 않는다")
    void release_withoutRecord_noop() {
        graceCancelUsageService.release(firstCancelId);

        assertThat(graceCancelUsageService.isUsed(CancelFixture.CUSTOMER_ID, TODAY)).isFalse();
    }

    private long saveCancel(long reservationId) {
        return reservationCancelRepository.saveAndFlush(ReservationCancel.builder()
                .reservationId(reservationId)
                .userId(CancelFixture.CUSTOMER_ID)
                .cancelType(CancelType.CUSTOMER)
                .policyRefundAmount(0)
                .refundAmount(20_000)
                .requestedAt(LocalDateTime.of(2026, 11, 1, 9, 0))
                .build()).getId();
    }
}
