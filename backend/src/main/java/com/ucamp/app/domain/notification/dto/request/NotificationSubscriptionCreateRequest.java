package com.ucamp.app.domain.notification.dto.request;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ucamp.app.domain.notification.entity.SubscriptionType;

/**
 * 알림 구독 등록 요청. STORE 는 shopId, CATEGORY_REGION 은 categoryCode·regionCode 를 보낸다.
 *
 * @param subscriptionType 구독 유형
 * @param shopId           매장 ID (업장 구독)
 * @param categoryCode     카테고리 코드 (카테고리·지역 구독)
 * @param regionCode       지역 코드 (카테고리·지역 구독)
 * @param startTime        알림 희망 시작 시각
 * @param endTime          알림 희망 종료 시각
 */
public record NotificationSubscriptionCreateRequest(
        @NotNull SubscriptionType subscriptionType,
        Long shopId,
        @Size(max = 50) String categoryCode,
        @Size(max = 20) String regionCode,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime) {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    public LocalDateTime localStartTime() {
        return toSeoulTime(startTime);
    }

    public LocalDateTime localEndTime() {
        return toSeoulTime(endTime);
    }

    // DB 컬럼이 TIMESTAMP(0) 이므로 초 단위로 맞춘다
    private static LocalDateTime toSeoulTime(OffsetDateTime time) {
        return time.atZoneSameInstant(ZONE_SEOUL).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
    }
}
