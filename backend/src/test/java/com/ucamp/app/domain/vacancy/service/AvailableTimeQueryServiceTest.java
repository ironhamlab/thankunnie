package com.ucamp.app.domain.vacancy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.vacancy.dto.response.AvailableTimesResponse;
import com.ucamp.app.domain.vacancy.repository.ShopStatusQueryRepository;
import com.ucamp.app.domain.vacancy.service.AvailabilityService.DayAvailability;

class AvailableTimeQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 9, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private AvailabilityService availabilityService;
    private ShopStatusQueryRepository shopStatusQueryRepository;
    private AvailableTimeQueryService availableTimeQueryService;

    @BeforeEach
    void setUp() {
        availabilityService = mock(AvailabilityService.class);
        shopStatusQueryRepository = mock(ShopStatusQueryRepository.class);
        Clock clock = Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);
        availableTimeQueryService = new AvailableTimeQueryService(availabilityService, shopStatusQueryRepository,
                clock);
    }

    @Test
    @DisplayName("운영 중인 매장이면 계산된 시작 시각과 연장 영업 시각을 한국 시간 오프셋으로 반환한다")
    void getAvailableTimes_activeShop_returnsOffsetTimes() {
        given(shopStatusQueryRepository.isActive(1L)).willReturn(true);
        given(availabilityService.getDayAvailability(1L, TODAY, 60)).willReturn(new DayAvailability(
                List.of(NOW.withHour(10), NOW.withHour(20)), List.of(NOW.withHour(20))));

        AvailableTimesResponse result = availableTimeQueryService.getAvailableTimes(1L, TODAY, 60);

        assertThat(result.durationMinutes()).isEqualTo(60);
        assertThat(result.startTimes()).hasSize(2);
        assertThat(result.startTimes().get(0).getHour()).isEqualTo(10);
        assertThat(result.startTimes().get(0).getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(result.extendedStartTimes()).singleElement()
                .satisfies(time -> assertThat(time.getHour()).isEqualTo(20));
    }

    @Test
    @DisplayName("오늘부터 60일째 날짜까지 조회할 수 있다")
    void getAvailableTimes_lastBookingDay_allowed() {
        LocalDate lastDay = TODAY.plusDays(59);
        given(shopStatusQueryRepository.isActive(1L)).willReturn(true);
        given(availabilityService.getDayAvailability(1L, lastDay, 30))
                .willReturn(new DayAvailability(List.of(), List.of()));

        assertThat(availableTimeQueryService.getAvailableTimes(1L, lastDay, 30).startTimes()).isEmpty();
    }

    @Test
    @DisplayName("지난 날짜나 60일을 넘는 날짜는 400 예외가 난다")
    void getAvailableTimes_outOfRangeDate_throwsOutOfRange() {
        assertThatThrownBy(() -> availableTimeQueryService.getAvailableTimes(1L, TODAY.minusDays(1), 30))
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_OUT_OF_RANGE);
        assertThatThrownBy(() -> availableTimeQueryService.getAvailableTimes(1L, TODAY.plusDays(60), 30))
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_OUT_OF_RANGE);
        verifyNoInteractions(availabilityService);
    }

    @Test
    @DisplayName("소요 시간이 0 이하이거나 하루를 넘으면 400 예외가 난다")
    void getAvailableTimes_invalidDuration_throwsInvalidDuration() {
        assertThatThrownBy(() -> availableTimeQueryService.getAvailableTimes(1L, TODAY, 0))
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_DURATION);
        assertThatThrownBy(() -> availableTimeQueryService.getAvailableTimes(1L, TODAY, 24 * 60 + 1))
                .extracting("errorCode").isEqualTo(ErrorCode.VACANCY_INVALID_DURATION);
        verifyNoInteractions(availabilityService);
    }

    @Test
    @DisplayName("없거나 운영 중지된 매장은 404 예외가 난다")
    void getAvailableTimes_inactiveShop_throwsNotFound() {
        given(shopStatusQueryRepository.isActive(99L)).willReturn(false);

        assertThatThrownBy(() -> availableTimeQueryService.getAvailableTimes(99L, TODAY, 30))
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(availabilityService);
    }
}
