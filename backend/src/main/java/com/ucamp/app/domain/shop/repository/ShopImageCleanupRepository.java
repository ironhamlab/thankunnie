package com.ucamp.app.domain.shop.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.shop.entity.ShopImageCleanup;

/** 이미지 정리 실패 작업의 조회 및 중복 실행 제어. */
public interface ShopImageCleanupRepository extends JpaRepository<ShopImageCleanup, Long> {

    boolean existsByStorageScopeAndFileName(String storageScope, String fileName);

    List<ShopImageCleanup> findByStorageScopeAndNextAttemptAtLessThanEqualOrderByCleanupIdAsc(
            String storageScope, LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ShopImageCleanup c where c.cleanupId = :cleanupId")
    Optional<ShopImageCleanup> findByIdForUpdate(@Param("cleanupId") Long cleanupId);
}
