package com.ucamp.app.domain.schedule.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.schedule.entity.ShopBusinessHour;

public interface ShopBusinessHourRepository extends JpaRepository<ShopBusinessHour, Long> {

    List<ShopBusinessHour> findByShopId(Long shopId);
}
