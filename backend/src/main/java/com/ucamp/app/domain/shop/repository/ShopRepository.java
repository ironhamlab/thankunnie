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

    /**
     * 매장 행을 비관적 쓰기 잠금(SELECT ... FOR UPDATE)으로 조회한다. 트랜잭션이 끝날 때 잠금이 풀린다.
     * 같은 매장의 시간을 바꾸는 작업을 줄 세울 때 common/lock/ShopLock 을 통해 사용한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shop s WHERE s.shopId = :shopId")
    Optional<Shop> findByIdForUpdate(@Param("shopId") Long shopId);
}
