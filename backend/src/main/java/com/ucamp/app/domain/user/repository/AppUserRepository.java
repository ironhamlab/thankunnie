package com.ucamp.app.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.user.entity.AppUser;
import com.ucamp.app.domain.user.entity.AppUserStatus;

/** 회원 계정 저장소. */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByUserIdAndStatus(Long userId, AppUserStatus status);

    boolean existsByPhoneAndStatus(String phone, AppUserStatus status);

    Optional<AppUser> findByPhoneAndStatus(String phone, AppUserStatus status);
}
