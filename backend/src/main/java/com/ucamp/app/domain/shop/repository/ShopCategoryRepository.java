package com.ucamp.app.domain.shop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.shop.entity.ShopCategory;

/** 매장 카테고리 유효성 확인 저장소. */
public interface ShopCategoryRepository extends JpaRepository<ShopCategory, Long> {

    List<ShopCategory> findAllByOrderByNameAsc();
}
