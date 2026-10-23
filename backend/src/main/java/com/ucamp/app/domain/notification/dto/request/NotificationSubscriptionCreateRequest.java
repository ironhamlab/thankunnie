package com.ucamp.app.domain.notification.dto.request;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ucamp.app.domain.notification.entity.SubscriptionType;

/**
 * 알림 구독 등록 요청. 매장 ID는 항상 보내고, 카테고리 구독만 categoryName·regionName 을 보낸다.
 *
 * @param subscriptionType 구독 유형
 * @param shopId           매장 ID
 * @param categoryName     카테고리명. 업장 구독이면 null
 * @param regionName       지역명. 업장 구독이면 null
 * @param startTime        알림 희망 시작 시각
 * @param endTime          알림 희망 종료 시각
 */
public record NotificationSubscriptionCreateRequest(
        @NotNull SubscriptionType subscriptionType,
        @NotNull Long shopId,
        @Size(max = 20) String categoryName,
        @Size(max = 20) String regionName,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime) {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * 희망 시작 시각을 서울 시간으로 변환한다.
     *
     * @return 초 단위로 맞춘 시작 시각
     */
    public LocalDateTime localStartTime() {
        return toSeoulTime(startTime);
    }

    /**
     * 희망 종료 시각을 서울 시간으로 변환한다.
     *
     * @return 초 단위로 맞춘 종료 시각
     */
    public LocalDateTime localEndTime() {
        return toSeoulTime(endTime);
    }

    // DB 컬럼이 TIMESTAMP(0) 이므로 초 단위로 맞춘다
    private static LocalDateTime toSeoulTime(OffsetDateTime time) {
        return time.atZoneSameInstant(ZONE_SEOUL).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
    }
}
