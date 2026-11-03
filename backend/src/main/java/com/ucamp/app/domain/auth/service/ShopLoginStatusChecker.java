package com.ucamp.app.domain.auth.service;

import org.springframework.stereotype.Component;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.AuthUserStatusChecker;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.domain.auth.entity.ShopAccountStatus;
import com.ucamp.app.domain.auth.repository.ShopAccountRepository;

/**
 * 매장 토큰은 요청마다 매장이 아직 로그인할 수 있는 상태인지 확인한다.
 * 관리자가 승인을 취소(REJECTED)하면 이미 발급한 토큰도 다음 요청부터 비로그인(401)이 된다.
 * 고객·관리자 토큰은 검사하지 않는다.
 */
@Component
public class ShopLoginStatusChecker implements AuthUserStatusChecker {

    private final ShopAccountRepository shopAccountRepository;

    public ShopLoginStatusChecker(ShopAccountRepository shopAccountRepository) {
        this.shopAccountRepository = shopAccountRepository;
    }

    @Override
    public boolean canAuthenticate(AuthUser authUser) {
        if (authUser.role() != UserRole.OWNER) {
            return true;
        }
        return shopAccountRepository.existsByShopIdAndStatusIn(authUser.shopId(), ShopAccountStatus.LOGIN_ALLOWED);
    }
}
