package com.ucamp.app.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.user.dto.response.UserProfileResponse;
import com.ucamp.app.domain.user.repository.AppUserRepository;

/** 회원(고객·관리자) 정보 조회. 매장 계정은 SHOP 행에 있어 여기서 다루지 않는다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;

    public UserProfileResponse getMyProfile(AuthUser loginUser) {
        // 매장의 userId 는 APP_USER 의 ID 가 아니므로 다른 회원이 조회되지 않도록 먼저 막는다
        loginUser.requireRole(UserRole.CUSTOMER, UserRole.ADMIN);
        return appUserRepository.findById(loginUser.userId())
                .map(UserProfileResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
