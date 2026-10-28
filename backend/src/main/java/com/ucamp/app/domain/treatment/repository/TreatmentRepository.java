package com.ucamp.app.domain.treatment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

    boolean existsByImageUrlEndingWith(String suffix);

    @EntityGraph(attributePaths = "shop")
    List<Treatment> findByShopShopIdOrderByTreatmentIdDesc(Long shopId);

    @EntityGraph(attributePaths = "shop")
    List<Treatment> findByShopShopIdAndStatusOrderByTreatmentIdDesc(Long shopId, TreatmentStatus status);

    @EntityGraph(attributePaths = "shop")
    Optional<Treatment> findByTreatmentIdAndShopShopId(Long treatmentId, Long shopId);
}
