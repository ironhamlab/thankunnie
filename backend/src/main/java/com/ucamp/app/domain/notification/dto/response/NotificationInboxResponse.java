package com.ucamp.app.domain.notification.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.notification.entity.NotificationInbox;
import com.ucamp.app.domain.notification.entity.NotificationInboxCategory;

/**
 * 받은 알림 한 줄.
 *
 * @param inboxId   받은 알림 ID
 * @param category  예약(RESERVATION) 또는 관심(INTEREST)
 * @param title     종 목록에 보이는 제목
 * @param body      보낸 문자와 같은 본문
 * @param linkPath  누르면 이동할 경로. 없으면 null
 * @param readAt    읽은 시각. null 이면 안 읽음
 * @param createdAt 알림이 생긴 시각
 */
public record NotificationInboxResponse(
        Long inboxId,
        NotificationInboxCategory category,
        String title,
        String body,
        String linkPath,
        OffsetDateTime readAt,
        OffsetDateTime createdAt) {

    /**
     * 받은 알림 엔티티를 응답으로 변환한다.
     *
     * @param inbox 받은 알림
     * @return 받은 알림 응답
     */
    public static NotificationInboxResponse from(NotificationInbox inbox) {
        return new NotificationInboxResponse(
                inbox.getId(),
                inbox.getCategory(),
                inbox.getTitle(),
                inbox.getBody(),
                inbox.getLinkPath(),
                TimeUtils.toOffset(inbox.getReadAt()),
                TimeUtils.toOffset(inbox.getCreatedAt()));
    }
}
