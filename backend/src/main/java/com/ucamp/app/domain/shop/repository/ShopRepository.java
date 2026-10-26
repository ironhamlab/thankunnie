package com.ucamp.app.domain.shop.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.shop.entity.Shop;

/** 매장 조회 저장소. */
public interface ShopRepository extends JpaRepository<Shop, Long> {

    /** 같은 매장의 대표 이미지 교체·삭제를 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shop s where s.shopId = :shopId")
    Optional<Shop> findByIdForUpdate(@Param("shopId") Long shopId);
}
