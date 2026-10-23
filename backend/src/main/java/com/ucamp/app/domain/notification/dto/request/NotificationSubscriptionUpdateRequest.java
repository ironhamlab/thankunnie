package com.ucamp.app.domain.notification.dto.request;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 알림 구독 수정 요청. 구독 유형은 바꿀 수 없으며, 기존 유형에 맞는 대상 값을 모두 보낸다.
 *
 * @param shopId       매장 ID
 * @param categoryName 카테고리명. 업장 구독이면 null
 * @param regionCode   지역코드. 업장 구독이면 null
 * @param startTime    알림 희망 시작 시각
 * @param endTime      알림 희망 종료 시각
 */
public record NotificationSubscriptionUpdateRequest(
        @NotNull Long shopId,
        @Size(max = 20) String categoryName,
        @Size(max = 20) String regionCode,
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
