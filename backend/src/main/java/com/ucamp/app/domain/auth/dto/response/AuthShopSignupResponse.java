package com.ucamp.app.domain.auth.dto.response;

import com.ucamp.app.domain.auth.entity.ShopAccount;
import com.ucamp.app.domain.auth.entity.ShopAccountStatus;

/**
 * 매장 가입 결과. 가입한 매장은 관리자 승인 전이라 토큰을 발급하지 않는다.
 *
 * @param status 가입 직후에는 항상 PENDING
 */
public record AuthShopSignupResponse(Long shopId, ShopAccountStatus status) {

    public static AuthShopSignupResponse from(ShopAccount account) {
        return new AuthShopSignupResponse(account.getShopId(), account.getStatus());
    }
}
