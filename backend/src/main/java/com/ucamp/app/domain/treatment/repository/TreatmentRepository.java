package com.ucamp.app.domain.treatment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.treatment.entity.Treatment;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {
}
