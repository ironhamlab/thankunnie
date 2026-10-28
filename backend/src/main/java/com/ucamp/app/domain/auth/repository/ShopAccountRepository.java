package com.ucamp.app.domain.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.auth.entity.ShopAccount;

/** 사장님(매장) 로그인 계정 저장소. */
public interface ShopAccountRepository extends JpaRepository<ShopAccount, Long> {

    Optional<ShopAccount> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
