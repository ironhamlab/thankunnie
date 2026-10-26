package com.ucamp.app.domain.treatment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.treatment.entity.Treatment;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

    @EntityGraph(attributePaths = {"shop", "category"})
    List<Treatment> findByShopShopIdOrderByTreatmentIdDesc(Long shopId);

    @EntityGraph(attributePaths = {"shop", "category"})
    Optional<Treatment> findByTreatmentIdAndShopShopId(Long treatmentId, Long shopId);
}
