package com.ucamp.app.domain.notification.dto.request;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 알림 수정 요청. 알림 유형은 바꿀 수 없으며, 기존 유형에 맞는 대상 값을 모두 보낸다.
 *
 * @param shopId      매장 ID. 카테고리 알림이면 null
 * @param treatmentId 시술 ID. 업장 알림이면 고객이 고른 시술, 카테고리 알림이면 null
 * @param categoryId  카테고리 ID. 업장 알림이면 null
 * @param regionCode  지역코드. 업장 알림이면 null
 * @param startTime   알림 희망 시작 시각
 * @param endTime     알림 희망 종료 시각
 */
@Schema(
        name = "NotificationSubscriptionUpdateRequest",
        example = """
                {
                  "shopId": 2,
                  "treatmentId": 10,
                  "categoryId": null,
                  "regionCode": null,
                  "startTime": "2026-10-29T14:00:00+09:00",
                  "endTime": "2026-10-31T20:00:00+09:00"
                }
                """)
public record NotificationSubscriptionUpdateRequest(
        @Schema(description = "매장 ID. STORE 알림일 때만", example = "2", nullable = true)
        Long shopId,
        @Schema(description = "시술 ID. STORE 일 때만. 그 매장의 판매 중인 일반 시술", example = "10", nullable = true)
        Long treatmentId,
        @Schema(description = "카테고리 ID. CATEGORY_REGION 일 때만", nullable = true)
        Long categoryId,
        @Schema(description = "지역코드. CATEGORY_REGION 일 때만", nullable = true)
        @Size(max = 20) String regionCode,
        @Schema(description = "알림 희망 시작. 업장 찜은 종료와 함께 비울 수 있다",
                example = "2026-10-29T14:00:00+09:00", nullable = true)
        OffsetDateTime startTime,
        @Schema(description = "알림 희망 종료. 시작보다 뒤이고 아직 지나지 않아야 함. 업장 찜은 시작과 함께 비울 수 있다",
                example = "2026-10-31T20:00:00+09:00", nullable = true)
        OffsetDateTime endTime) {

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

    // DB 컬럼이 TIMESTAMP(0) 이므로 초 단위로 맞춘다. 업장 찜은 시간을 비울 수 있다
    private static LocalDateTime toSeoulTime(OffsetDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZoneSameInstant(ZONE_SEOUL).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
    }
}
