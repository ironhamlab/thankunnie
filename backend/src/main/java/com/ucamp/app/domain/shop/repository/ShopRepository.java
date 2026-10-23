package com.ucamp.app.domain.shop.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.shop.entity.Shop;

/** 매장 정보 저장소. */
public interface ShopRepository extends JpaRepository<Shop, Long> {
}
