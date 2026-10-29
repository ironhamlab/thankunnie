package com.ucamp.app.domain.explore.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.treatment.entity.Treatment;

/**
 * 고객 빈자리 상세 조회 (C-03). 시술(A)·매장(A) 엔티티를 읽기만 하고 고치지 않는다.
 * 매장 노출 상태(SHOP.STATUS)는 Shop 엔티티에 없어 같은 테이블을 매핑한 ShopAccount 로 거른다.
 */
public interface ExploreVacancyDetailRepository extends Repository<Treatment, Long> {

    // JPQL 문자열 안에서는 import가 되지 않아 enum은 패키지 이름까지 적는다.
    String SHOP_ACTIVE = "com.ucamp.app.domain.auth.entity.ShopAccountStatus.ACTIVE";
    String TREATMENT_ACTIVE = "com.ucamp.app.domain.treatment.entity.TreatmentStatus.ACTIVE";

    String FIND_DETAIL = """
            select new com.ucamp.app.domain.explore.repository.ExploreVacancyDetailCandidate(
                   s.shopId, s.name, s.regionCode, s.address, s.addressDetail, s.latitude, s.longitude,
                   s.phone, s.description, s.notice, s.imageUrl,
                   t.treatmentId, t.name, t.description, c.categoryId, c.name, t.treatmentType,
                   t.durationMinutes, t.regularPrice, t.price, t.lastMinutePrice,
                   t.modelCondition, t.preparationNotes, t.photoNotice, t.imageUrl)
            from Treatment t
            join t.shop s
            join ShopAccount a on a.shopId = s.shopId
            join ShopCategory c on c.categoryId = s.categoryId
            where t.treatmentId = :treatmentId
              and t.status = \
            """
            + TREATMENT_ACTIVE
            + """
              and a.status = \
            """
            + SHOP_ACTIVE;

    /**
     * 판매 중인 시술과 노출 중인 매장 정보를 조회한다.
     *
     * @param treatmentId 시술 ID
     * @return 상세 후보. 시술이 없거나 시술·매장이 비활성이면 empty
     */
    @Query(FIND_DETAIL)
    Optional<ExploreVacancyDetailCandidate> findDetail(@Param("treatmentId") Long treatmentId);
}
