package com.ucamp.app.domain.cancel.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.cancel.entity.GraceCancelUsage;
import com.ucamp.app.domain.cancel.entity.GraceCancelUsageId;

public interface GraceCancelUsageRepository extends JpaRepository<GraceCancelUsage, GraceCancelUsageId> {

    boolean existsByUserIdAndUsedDate(Long userId, LocalDate usedDate);

    List<GraceCancelUsage> findAllByCancelId(Long cancelId);
}
