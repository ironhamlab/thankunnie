package com.ucamp.app.domain.cancel.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.cancel.entity.GraceCancelUsage;
import com.ucamp.app.domain.cancel.entity.GraceCancelUsageId;

public interface GraceCancelUsageRepository extends JpaRepository<GraceCancelUsage, GraceCancelUsageId> {

    boolean existsByUserIdAndUsedDate(Long userId, LocalDate usedDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from GraceCancelUsage g where g.cancelId = :cancelId")
    int deleteByCancelId(@Param("cancelId") Long cancelId);
}
