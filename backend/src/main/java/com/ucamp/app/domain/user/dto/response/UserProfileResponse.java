package com.ucamp.app.domain.user.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.user.entity.AppUser;

/**
 * 내 회원 정보.
 *
 * @param userId    회원 ID
 * @param name      이름
 * @param phone     휴대폰 번호. 문자 알림 수신 번호다
 * @param createdAt 가입 시각
 */
public record UserProfileResponse(Long userId, String name, String phone, OffsetDateTime createdAt) {

    public static UserProfileResponse from(AppUser user) {
        return new UserProfileResponse(
                user.getUserId(),
                user.getName(),
                user.getPhone(),
                TimeUtils.toOffset(user.getCreatedAt()));
    }
}
