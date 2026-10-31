package com.ucamp.app.domain.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.domain.notification.entity.NotificationEvent;
import com.ucamp.app.domain.notification.entity.NotificationEventType;
import com.ucamp.app.domain.notification.repository.NotificationEventRepository;
import com.ucamp.app.domain.notification.repository.ShopCategoryRegionRepository;
import com.ucamp.app.domain.notification.repository.VacancySlotRepository;
import com.ucamp.app.domain.vacancy.repository.ShopStatusQueryRepository;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

/**
 * 알림을 새로 저장한 회원에게 지금 보낼 수 있는, 이미 만들어진 빈자리 이벤트를 고른다.
 * 임박·초임박·재공개 이벤트만 본다. 아직 그 구간이 아니라 이벤트가 없는 먼 빈자리는 여기서 찾지 않는다.
 * 그 빈자리는 나중에 기존 스케줄러가 이벤트를 만들 때 보낸다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExistingVacancyTargetService {

    private final NotificationEventRepository notificationEventRepository;
    private final VacancySlotRepository vacancySlotRepository;
    private final AvailabilityService availabilityService;
    private final ShopStatusQueryRepository shopStatusQueryRepository;
    private final ShopCategoryRegionRepository shopCategoryRegionRepository;
    private final Clock clock;

    /**
     * 희망 시간 안이고 아직 예약 가능한 이벤트만 돌려준다.
     * 희망 시간이 없거나 희망 종료가 이미 지났으면 빈 목록이다.
     *
     * @param shopId    업장 구독의 매장 ID
     * @param hopeStart 희망 시작
     * @param hopeEnd   희망 종료
     * @return 이 회원에게 추가로 보낼 수 있는 이벤트. 시작 시각이 빠른 순이다
     */
    public List<NotificationEvent> findForStore(Long shopId, LocalDateTime hopeStart, LocalDateTime hopeEnd) {
        if (shopId == null || hopeStart == null || hopeEnd == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        // 구독 매칭과 같이, 희망 종료가 지금이거나 과거면 보낼 대상이 없다.
        if (!hopeEnd.isAfter(now)) {
            return List.of();
        }

        List<NotificationEvent> inHopeTime = notificationEventRepository.findByShopAndSlotStartInHopeTime(
                shopId, hopeStart, hopeEnd);
        List<NotificationEvent> targets = new ArrayList<>();
        for (NotificationEvent event : inHopeTime) {
            if (stillSellable(event, now)) {
                targets.add(event);
            }
        }
        return targets;
    }

    /**
     * 이 카테고리·지역의 영업 중 매장에서, 희망 시간 안이고 아직 예약 가능한 이벤트를 모은다.
     * 매장마다 업장 구독과 같은 기준으로 고르고, 시작 시각이 빠른 순으로 돌려준다.
     *
     * @param categoryId 카테고리 ID
     * @param regionCode 지역코드
     * @param hopeStart  희망 시작
     * @param hopeEnd    희망 종료
     * @return 이 회원에게 추가로 보낼 수 있는 이벤트
     */
    public List<NotificationEvent> findForCategoryRegion(Long categoryId, String regionCode,
            LocalDateTime hopeStart, LocalDateTime hopeEnd) {
        if (categoryId == null || regionCode == null || regionCode.isBlank()) {
            return List.of();
        }
        List<NotificationEvent> targets = new ArrayList<>();
        for (Long shopId : shopCategoryRegionRepository.findActiveShopIds(categoryId, regionCode)) {
            targets.addAll(findForStore(shopId, hopeStart, hopeEnd));
        }
        targets.sort((left, right) -> left.getSlotStartsAt().compareTo(right.getSlotStartsAt()));
        return targets;
    }

    // 발송 직전 확인과 같은 기준이다. 재공개는 화면 예약 가능 여부, 임박·초임박은 OPEN 판매 여부.
    private boolean stillSellable(NotificationEvent event, LocalDateTime now) {
        if (event.getEventType() == NotificationEventType.REOPENED) {
            if (!shopStatusQueryRepository.isActive(event.getShopId())) {
                return false;
            }
            int durationMinutes = (int) Duration.between(event.getSlotStartsAt(), event.getSlotEndsAt()).toMinutes();
            if (durationMinutes <= 0) {
                return false;
            }
            return availabilityService.isAvailable(
                    event.getShopId(), event.getSlotStartsAt(), durationMinutes);
        }
        return vacancySlotRepository.isOpenSlotSellable(event.getExceptionId(), now);
    }
}
