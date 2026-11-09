package com.ucamp.app.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventType;
import com.ucamp.app.domain.notification.repository.NotificationEventRepository;
import com.ucamp.app.domain.notification.repository.ShopCategoryRegionRepository;
import com.ucamp.app.domain.notification.repository.VacancySlotRepository;
import com.ucamp.app.domain.vacancy.repository.ShopStatusQueryRepository;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

class ExistingVacancyTargetServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 17, 0);
    private static final Long SHOP_ID = 1L;
    private static final LocalDateTime HOPE_START = LocalDateTime.of(2026, 10, 31, 17, 3);
    private static final LocalDateTime HOPE_END = LocalDateTime.of(2026, 10, 31, 21, 0);

    private NotificationEventRepository notificationEventRepository;
    private VacancySlotRepository vacancySlotRepository;
    private AvailabilityService availabilityService;
    private ShopStatusQueryRepository shopStatusQueryRepository;
    private ShopCategoryRegionRepository shopCategoryRegionRepository;
    private ExistingVacancyTargetService existingVacancyTargetService;

    @BeforeEach
    void setUp() {
        notificationEventRepository = mock(NotificationEventRepository.class);
        vacancySlotRepository = mock(VacancySlotRepository.class);
        availabilityService = mock(AvailabilityService.class);
        shopStatusQueryRepository = mock(ShopStatusQueryRepository.class);
        shopCategoryRegionRepository = mock(ShopCategoryRegionRepository.class);
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        existingVacancyTargetService = new ExistingVacancyTargetService(
                notificationEventRepository,
                vacancySlotRepository,
                availabilityService,
                shopStatusQueryRepository,
                shopCategoryRegionRepository,
                clock);
    }

    @Test
    @DisplayName("희망 시간이 없으면 이벤트를 찾지 않는다")
    void findForStore_withoutHopeTime_returnsEmpty() {
        assertThat(existingVacancyTargetService.findForStore(SHOP_ID, null, HOPE_END)).isEmpty();
        assertThat(existingVacancyTargetService.findForStore(SHOP_ID, HOPE_START, null)).isEmpty();
        assertThat(existingVacancyTargetService.findForStore(null, HOPE_START, HOPE_END)).isEmpty();

        verify(notificationEventRepository, never()).findByShopAndSlotStartInHopeTime(any(), any(), any());
    }

    @Test
    @DisplayName("희망 종료가 지금이거나 과거면 보내지 않는다")
    void findForStore_hopeEndNotAfterNow_returnsEmpty() {
        assertThat(existingVacancyTargetService.findForStore(SHOP_ID, HOPE_START, NOW)).isEmpty();
        assertThat(existingVacancyTargetService.findForStore(SHOP_ID, HOPE_START, NOW.minusMinutes(1))).isEmpty();

        verify(notificationEventRepository, never()).findByShopAndSlotStartInHopeTime(any(), any(), any());
    }

    @Test
    @DisplayName("희망 시간 안이고 아직 예약 가능한 임박·초임박·재공개만 고른다")
    void findForStore_keepsSellableEventsInsideHopeTime() {
        NotificationEvent lastMinute = event(NotificationEventType.LAST_MINUTE, 10L, NOW.plusHours(1), NOW.plusHours(2));
        NotificationEvent imminent = event(NotificationEventType.IMMINENT, 11L, NOW.plusHours(4), NOW.plusHours(5));
        NotificationEvent reopened = event(NotificationEventType.REOPENED, null, NOW.plusHours(2), NOW.plusHours(3));
        NotificationEvent closed = event(NotificationEventType.LAST_MINUTE, 12L, NOW.plusHours(2), NOW.plusHours(3));
        given(notificationEventRepository.findByShopAndSlotStartInHopeTime(SHOP_ID, HOPE_START, HOPE_END))
                .willReturn(List.of(lastMinute, imminent, closed, reopened));
        given(vacancySlotRepository.isOpenSlotSellable(10L, NOW)).willReturn(true);
        given(vacancySlotRepository.isOpenSlotSellable(11L, NOW)).willReturn(true);
        given(vacancySlotRepository.isOpenSlotSellable(12L, NOW)).willReturn(false);
        given(shopStatusQueryRepository.isActive(SHOP_ID)).willReturn(true);
        given(availabilityService.isAvailable(SHOP_ID, reopened.getSlotStartsAt(), 60)).willReturn(true);

        List<NotificationEvent> targets = existingVacancyTargetService.findForStore(SHOP_ID, HOPE_START, HOPE_END);

        assertThat(targets).containsExactly(lastMinute, imminent, reopened);
    }

    @Test
    @DisplayName("같은 시작 시각의 재공개는 이벤트 번호가 가장 큰 한 건만 남긴다")
    void findForStore_sameReopenedSlot_keepsLatestOnly() {
        LocalDateTime slotStart = NOW.plusHours(2);
        NotificationEvent older = event(NotificationEventType.REOPENED, null, slotStart, slotStart.plusHours(1));
        NotificationEvent latest = event(NotificationEventType.REOPENED, null, slotStart, slotStart.plusHours(1));
        NotificationEvent imminent = event(NotificationEventType.IMMINENT, 11L, slotStart, slotStart.plusHours(1));
        ReflectionTestUtils.setField(older, "id", 1L);
        ReflectionTestUtils.setField(latest, "id", 2L);
        ReflectionTestUtils.setField(imminent, "id", 3L);
        given(notificationEventRepository.findByShopAndSlotStartInHopeTime(SHOP_ID, HOPE_START, HOPE_END))
                .willReturn(List.of(older, latest, imminent));
        given(shopStatusQueryRepository.isActive(SHOP_ID)).willReturn(true);
        given(availabilityService.isAvailable(SHOP_ID, slotStart, 60)).willReturn(true);
        given(vacancySlotRepository.isOpenSlotSellable(11L, NOW)).willReturn(true);

        assertThat(existingVacancyTargetService.findForStore(SHOP_ID, HOPE_START, HOPE_END))
                .containsExactly(latest, imminent);
    }

    @Test
    @DisplayName("재공개는 매장이 영업 중이 아니거나 예약이 불가능하면 뺀다")
    void findForStore_reopenedNotBookable_isExcluded() {
        NotificationEvent inactiveShop = event(
                NotificationEventType.REOPENED, null, NOW.plusHours(1), NOW.plusHours(2));
        NotificationEvent unavailable = event(
                NotificationEventType.REOPENED, null, NOW.plusHours(2), NOW.plusHours(3));
        NotificationEvent zeroLength = event(
                NotificationEventType.REOPENED, null, NOW.plusHours(3), NOW.plusHours(3));
        given(notificationEventRepository.findByShopAndSlotStartInHopeTime(SHOP_ID, HOPE_START, HOPE_END))
                .willReturn(List.of(inactiveShop, unavailable, zeroLength));
        given(shopStatusQueryRepository.isActive(SHOP_ID)).willReturn(false);

        assertThat(existingVacancyTargetService.findForStore(SHOP_ID, HOPE_START, HOPE_END)).isEmpty();
        verify(availabilityService, never()).isAvailable(any(), any(), anyInt());
    }

    @Test
    @DisplayName("영업 중인 재공개도 시간이 없거나 예약이 불가능하면 뺀다")
    void findForStore_activeReopenedNotBookable_isExcluded() {
        NotificationEvent unavailable = event(
                NotificationEventType.REOPENED, null, NOW.plusHours(1), NOW.plusHours(2));
        NotificationEvent zeroLength = event(
                NotificationEventType.REOPENED, null, NOW.plusHours(2), NOW.plusHours(2));
        given(notificationEventRepository.findByShopAndSlotStartInHopeTime(SHOP_ID, HOPE_START, HOPE_END))
                .willReturn(List.of(unavailable, zeroLength));
        given(shopStatusQueryRepository.isActive(SHOP_ID)).willReturn(true);
        given(availabilityService.isAvailable(SHOP_ID, unavailable.getSlotStartsAt(), 60)).willReturn(false);

        assertThat(existingVacancyTargetService.findForStore(SHOP_ID, HOPE_START, HOPE_END)).isEmpty();
        verify(availabilityService, never()).isAvailable(SHOP_ID, zeroLength.getSlotStartsAt(), 0);
    }

    @Test
    @DisplayName("카테고리·지역은 영업 중인 그 매장들의 예약 가능한 이벤트만 시작 시각 순으로 모은다")
    void findForCategoryRegion_collectsSellableEventsFromActiveShops() {
        NotificationEvent later = event(NotificationEventType.LAST_MINUTE, 10L, NOW.plusHours(2), NOW.plusHours(3));
        NotificationEvent sooner = event(NotificationEventType.IMMINENT, 11L, NOW.plusHours(1), NOW.plusHours(2));
        given(shopCategoryRegionRepository.findActiveShopIds(10L, "SEOUL_GANGNAM")).willReturn(List.of(1L, 3L));
        given(notificationEventRepository.findByShopAndSlotStartInHopeTime(1L, HOPE_START, HOPE_END))
                .willReturn(List.of(later));
        given(notificationEventRepository.findByShopAndSlotStartInHopeTime(3L, HOPE_START, HOPE_END))
                .willReturn(List.of(sooner));
        given(vacancySlotRepository.isOpenSlotSellable(10L, NOW)).willReturn(true);
        given(vacancySlotRepository.isOpenSlotSellable(11L, NOW)).willReturn(true);

        assertThat(existingVacancyTargetService.findForCategoryRegion(10L, "SEOUL_GANGNAM", HOPE_START, HOPE_END))
                .containsExactly(sooner, later);
    }

    private static NotificationEvent event(NotificationEventType eventType, Long exceptionId,
            LocalDateTime startsAt, LocalDateTime endsAt) {
        return NotificationEvent.builder()
                .eventType(eventType)
                .exceptionId(exceptionId)
                .shopId(SHOP_ID)
                .slotStartsAt(startsAt)
                .slotEndsAt(endsAt)
                .build();
    }
}
