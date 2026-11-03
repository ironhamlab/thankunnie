package com.ucamp.app.domain.auth.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.auth.entity.ShopAccount;
import com.ucamp.app.domain.auth.entity.ShopAccountStatus;

/** 매장 로그인 계정 저장소. */
public interface ShopAccountRepository extends JpaRepository<ShopAccount, Long> {

    Optional<ShopAccount> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByBusinessNumber(String businessNumber);

    boolean existsByShopIdAndStatus(Long shopId, ShopAccountStatus status);

    Page<ShopAccount> findByStatus(ShopAccountStatus status, Pageable pageable);

    /**
     * 상태가 같은 매장을 매장명·아이디·연락처로 찾는다. 연락처는 하이픈을 뺀 값으로도 비교한다.
     *
     * @param keyword      대문자로 바꾸고 %·_ 를 ! 로 이스케이프한 like 패턴
     * @param phoneKeyword keyword 에서 하이픈을 뺀 like 패턴
     */
    @Query("""
            select s from ShopAccount s
            where s.status = :status
              and (upper(s.name) like :keyword escape '!'
                   or upper(s.loginId) like :keyword escape '!'
                   or replace(s.phone, '-', '') like :phoneKeyword escape '!')
            """)
    Page<ShopAccount> searchByStatus(@Param("status") ShopAccountStatus status, @Param("keyword") String keyword,
            @Param("phoneKeyword") String phoneKeyword, Pageable pageable);

    /** 관리자 두 명이 같은 신청을 동시에 처리해도 한 번만 반영되도록 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ShopAccount s where s.shopId = :shopId")
    Optional<ShopAccount> findByIdForUpdate(@Param("shopId") Long shopId);
}
