package com.ucamp.app.domain.auth.dto.response;

import java.time.OffsetDateTime;

import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.auth.entity.ShopAccount;
import com.ucamp.app.domain.auth.entity.ShopAccountStatus;

/**
 * 관리자가 보는 매장 가입 신청.
 *
 * @param categoryName   업종 이름. 분류가 사라졌으면 null
 * @param businessNumber 사업자등록번호 숫자 10자리. 번호 없이 가입한 기존 매장은 null
 * @param rejectReason   거절 사유. 거절 상태가 아니면 null
 * @param appliedAt      신청 시각
 */
public record ShopApplicationResponse(
        Long shopId,
        String loginId,
        String name,
        Long categoryId,
        String categoryName,
        String address,
        String addressDetail,
        String phone,
        String businessNumber,
        String description,
        ShopAccountStatus status,
        String rejectReason,
        OffsetDateTime appliedAt) {

    public static ShopApplicationResponse of(ShopAccount shop, String categoryName) {
        return new ShopApplicationResponse(
                shop.getShopId(),
                shop.getLoginId(),
                shop.getName(),
                shop.getCategoryId(),
                categoryName,
                shop.getAddress(),
                shop.getAddressDetail(),
                shop.getPhone(),
                shop.getBusinessNumber(),
                shop.getDescription(),
                shop.getStatus(),
                shop.getRejectReason(),
                TimeUtils.toOffset(shop.getCreatedAt()));
    }
}
