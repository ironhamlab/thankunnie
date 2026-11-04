package com.ucamp.app.domain.user.service;

import org.springframework.stereotype.Component;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.AuthUserStatusChecker;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.domain.user.entity.AppUserStatus;
import com.ucamp.app.domain.user.repository.AppUserRepository;

/**
 * 고객·관리자 토큰은 요청마다 회원이 아직 사용 중(ACTIVE)인지 확인한다.
 * 탈퇴(INACTIVE)하면 이미 발급한 토큰도 다음 요청부터 비로그인(401)이 되어 예약·구독을 할 수 없다.
 * 매장 토큰은 ShopLoginStatusChecker 가 본다.
 */
@Component
public class CustomerLoginStatusChecker implements AuthUserStatusChecker {

    private final AppUserRepository appUserRepository;

    public CustomerLoginStatusChecker(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public boolean canAuthenticate(AuthUser authUser) {
        if (authUser.role() == UserRole.OWNER) {
            return true;
        }
        return appUserRepository.existsByUserIdAndStatus(authUser.userId(), AppUserStatus.ACTIVE);
    }
}
