package com.ucamp.app.domain.shop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.shop.entity.ShopCustomerMemo;

public interface ShopCustomerMemoRepository extends JpaRepository<ShopCustomerMemo, Long> {

    Optional<ShopCustomerMemo> findByShopIdAndUserId(Long shopId, Long userId);
}
