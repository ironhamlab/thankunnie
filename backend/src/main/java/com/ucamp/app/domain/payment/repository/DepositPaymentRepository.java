package com.ucamp.app.domain.payment.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.payment.entity.DepositPayment;

public interface DepositPaymentRepository extends JpaRepository<DepositPayment, Long> {

    /**
     * 환불 상태 변경용 조회. 같은 결제의 동시 환불을 막기 위해 행을 잠근다.
     * 잠금은 상태만 바꾸는 짧은 트랜잭션에서만 잡고, PG 호출 중에는 잡지 않는다.
     */
    /**
     * 포트원 결제 고유번호(imp_uid)로 저장된 예약금결제를 찾는다. 같은 결제를 두 번 확정하지 않으려고 쓴다.
     */
    Optional<DepositPayment> findByPgTransactionKey(String pgTransactionKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select p from DepositPayment p where p.depositPaymentId = :depositPaymentId")
    Optional<DepositPayment> findByIdForUpdate(@Param("depositPaymentId") Long depositPaymentId);
}
