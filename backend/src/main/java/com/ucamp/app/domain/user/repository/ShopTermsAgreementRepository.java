package com.ucamp.app.domain.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.user.entity.ShopTermsAgreement;

/** 매장 약관 동의 저장소. */
public interface ShopTermsAgreementRepository extends JpaRepository<ShopTermsAgreement, Long> {

    List<ShopTermsAgreement> findByShopId(Long shopId);
}
