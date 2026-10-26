package com.ucamp.app.domain.payment.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.payment.entity.DepositPayment;

public interface DepositPaymentRepository extends JpaRepository<DepositPayment, Long> {

    /**
     * 환불용 조회. 같은 결제에 환불 요청이 동시에 들어와 중복 환불되지 않도록 행을 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DepositPayment p where p.depositPaymentId = :depositPaymentId")
    Optional<DepositPayment> findByIdForUpdate(@Param("depositPaymentId") Long depositPaymentId);
}
