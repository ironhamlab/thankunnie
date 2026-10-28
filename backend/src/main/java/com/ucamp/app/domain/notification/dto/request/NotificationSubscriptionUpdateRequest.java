package com.ucamp.app.domain.notification.dto.request;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 알림 구독 수정 요청. 구독 유형은 바꿀 수 없으며, 기존 유형에 맞는 대상 값을 모두 보낸다.
 *
 * @param shopId     매장 ID. 카테고리 구독이면 null
 * @param categoryId 카테고리 ID. 업장 구독이면 null
 * @param regionCode 지역코드. 업장 구독이면 null
 * @param startTime  알림 희망 시작 시각
 * @param endTime    알림 희망 종료 시각
 */
@Schema(
        name = "NotificationSubscriptionUpdateRequest",
        example = """
                {
                  "shopId": 2,
                  "categoryId": null,
                  "regionCode": null,
                  "startTime": "2026-10-29T14:00:00+09:00",
                  "endTime": "2026-10-31T20:00:00+09:00"
                }
                """)
public record NotificationSubscriptionUpdateRequest(
        @Schema(description = "매장 ID. STORE 구독일 때만", example = "2", nullable = true)
        Long shopId,
        @Schema(description = "카테고리 ID. CATEGORY_REGION 일 때만", nullable = true)
        Long categoryId,
        @Schema(description = "지역코드. CATEGORY_REGION 일 때만", nullable = true)
        @Size(max = 20) String regionCode,
        @Schema(description = "알림 희망 시작", example = "2026-10-29T14:00:00+09:00")
        @NotNull OffsetDateTime startTime,
        @Schema(description = "알림 희망 종료. 시작보다 뒤이고 아직 지나지 않아야 함",
                example = "2026-10-31T20:00:00+09:00")
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
