package com.ucamp.app.domain.user.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.user.entity.Terms;

/** 약관 마스터 저장소. */
public interface TermsRepository extends JpaRepository<Terms, Long> {

    /** 코드별 최신 버전을 고르기 쉽게 코드·버전 순으로 전체를 읽는다. 약관은 몇 행뿐이다. */
    List<Terms> findAllByOrderByCodeAscVersionAsc();
}
