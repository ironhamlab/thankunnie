package com.ucamp.app.domain.explore.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.treatment.entity.Treatment;

/**
 * 고객 매장 상세 조회. 매장(A)·시술 엔티티를 읽기만 하고 고치지 않는다.
 * 매장 노출 상태(SHOP.STATUS)는 Shop 엔티티에 없어 같은 테이블을 매핑한 ShopAccount 로 거른다.
 */
public interface ExploreShopDetailRepository extends Repository<Treatment, Long> {

    // JPQL 문자열 안에서는 import가 되지 않아 enum은 패키지 이름까지 적는다.
    String SHOP_ACTIVE = "com.ucamp.app.domain.auth.entity.ShopAccountStatus.ACTIVE";
    String TREATMENT_ACTIVE = "com.ucamp.app.domain.treatment.entity.TreatmentStatus.ACTIVE";

    String FIND_SHOP = """
            select new com.ucamp.app.domain.explore.repository.ExploreShopCandidate(
                   s.shopId, s.name, c.categoryId, c.name, s.regionCode, s.address, s.addressDetail,
                   s.latitude, s.longitude, s.phone, s.description, s.notice, s.imageUrl)
            from Shop s
            join ShopAccount a on a.shopId = s.shopId
            join ShopCategory c on c.categoryId = s.categoryId
            where s.shopId = :shopId
              and a.status = \
            """
            + SHOP_ACTIVE;

    String FIND_SHOPS = """
            select new com.ucamp.app.domain.explore.repository.ExploreShopCandidate(
                   s.shopId, s.name, c.categoryId, c.name, s.regionCode, s.address, s.addressDetail,
                   s.latitude, s.longitude, s.phone, s.description, s.notice, s.imageUrl)
            from Shop s
            join ShopAccount a on a.shopId = s.shopId
            join ShopCategory c on c.categoryId = s.categoryId
            where (:categoryId is null or s.categoryId = :categoryId)
              and (:keyword is null
                   or upper(s.name) like :keyword escape '!'
                   or upper(s.address) like :keyword escape '!'
                   or exists (select t.treatmentId from Treatment t
                              where t.shop.shopId = s.shopId
                                and t.status = """
            + TREATMENT_ACTIVE
            + """

                                and upper(t.name) like :keyword escape '!'))
              and a.status = \
            """
            + SHOP_ACTIVE
            + """

            order by s.shopId
            """;

    String FIND_TREATMENTS = """
            select new com.ucamp.app.domain.explore.repository.ExploreShopTreatmentCandidate(
                   t.treatmentId, t.name, t.description, t.treatmentType, t.durationMinutes,
                   t.regularPrice, t.price, t.modelCondition, t.photoNotice, t.imageUrl)
            from Treatment t
            where t.shop.shopId = :shopId
              and t.status = \
            """
            + TREATMENT_ACTIVE
            + """

            order by t.treatmentId
            """;

    /**
     * 노출 중인 매장 정보를 조회한다.
     *
     * @param shopId 매장 ID
     * @return 매장 정보. 매장이 없거나 비활성이면 empty
     */
    @Query(FIND_SHOP)
    Optional<ExploreShopCandidate> findShop(@Param("shopId") Long shopId);

    /**
     * 노출 중인 매장을 빈자리 유무와 상관없이 매장 ID 순으로 조회한다.
     *
     * @param categoryId 해당 분야 매장만. null 이면 전체
     * @param keyword    매장명·주소·판매 중인 시술명 LIKE 패턴(대문자, ! 로 이스케이프). null 이면 조건 없음
     * @return 노출 중인 매장 목록
     */
    @Query(FIND_SHOPS)
    List<ExploreShopCandidate> findShops(@Param("categoryId") Long categoryId, @Param("keyword") String keyword);

    /**
     * 매장의 판매 중인 시술을 시술 ID 순으로 조회한다.
     *
     * @param shopId 매장 ID
     * @return 판매 중인 시술 목록
     */
    @Query(FIND_TREATMENTS)
    List<ExploreShopTreatmentCandidate> findTreatments(@Param("shopId") Long shopId);
}
