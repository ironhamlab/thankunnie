package com.ucamp.app.domain.cancel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ucamp.app.domain.cancel.entity.ReservationCancel;

public interface ReservationCancelRepository extends JpaRepository<ReservationCancel, Long> {

    Optional<ReservationCancel> findByReservationId(Long reservationId);
}
