package com.ucamp.app.domain.user.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.user.entity.UserTermsAgreement;

/** 회원 약관 동의 저장소. */
public interface UserTermsAgreementRepository extends JpaRepository<UserTermsAgreement, Long> {

    List<UserTermsAgreement> findByUserId(Long userId);

    List<UserTermsAgreement> findByUserIdAndTermsIdIn(Long userId, Collection<Long> termsIds);

    boolean existsByUserIdAndTermsIdAndWithdrawnAtIsNull(Long userId, Long termsId);
}
