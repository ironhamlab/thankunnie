package com.ucamp.app.domain.treatment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

    boolean existsByImageUrlEndingWith(String suffix);

    @EntityGraph(attributePaths = "shop")
    List<Treatment> findByShopShopIdAndStatusNotOrderByTreatmentIdDesc(Long shopId, TreatmentStatus status);

    @EntityGraph(attributePaths = "shop")
    List<Treatment> findByShopShopIdAndStatusOrderByTreatmentIdDesc(Long shopId, TreatmentStatus status);

    @EntityGraph(attributePaths = "shop")
    Optional<Treatment> findByTreatmentIdAndShopShopId(Long treatmentId, Long shopId);

    /**
     * 매장 시술별로 아직 끝나지 않은 예약 수를 센다. 기준은 회원 탈퇴 검사와 같다:
     * 환불 처리 중 취소, 만료 전 결제 대기, 종료 전 확정 예약.
     * 예약 엔티티는 예약 도메인 소유라 의존하지 않도록 RESERVATION 테이블을 직접 조회한다.
     *
     * @return [TREATMENT_ID, 건수] 목록. 남은 예약이 없는 시술은 포함하지 않는다
     */
    @Query(value = """
            SELECT r.TREATMENT_ID, COUNT(*)
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID = :shopId
              AND (r.STATUS = 'CANCEL_PENDING'
                   OR r.STATUS = 'HOLD' AND r.HOLD_EXPIRES_AT > :now
                   OR r.STATUS = 'CONFIRMED' AND r.ENDS_AT > :now)
            GROUP BY r.TREATMENT_ID
            """, nativeQuery = true)
    List<Object[]> countRemainingReservationsByShop(@Param("shopId") Long shopId,
            @Param("now") LocalDateTime now);

    @EntityGraph(attributePaths = "shop")
    Optional<Treatment> findByTreatmentIdAndShopShopIdAndStatusNot(Long treatmentId, Long shopId,
            TreatmentStatus status);
}
