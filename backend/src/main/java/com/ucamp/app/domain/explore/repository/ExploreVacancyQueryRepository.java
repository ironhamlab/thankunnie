package com.ucamp.app.domain.explore.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.explore.repository.ExploreVacancyCondition.MapBounds;
import com.ucamp.app.domain.schedule.entity.ShopScheduleException;
import com.ucamp.app.domain.treatment.entity.TreatmentType;

/**
 * 고객 빈자리 탐색 후보 조회 (C). 매장(A)·시술·시간대 열기(B) 엔티티를 읽기만 하고 고치지 않는다.
 * 조회 기간과 겹치는 OPEN 시간대 × 그 매장의 판매 중인 시술을 돌려준다. 임박 빈자리는 OPEN 없이 매장 × 시술만 돌려준다.
 * 매장 노출 상태(SHOP.STATUS)는 Shop 엔티티에 없어 같은 테이블을 매핑한 ShopAccount 로 거른다.
 */
public interface ExploreVacancyQueryRepository extends Repository<ShopScheduleException, Long> {

    // JPQL 문자열 안에서는 import가 되지 않아 enum은 패키지 이름까지 적는다.
    String OPEN = "com.ucamp.app.domain.schedule.entity.ScheduleExceptionType.OPEN";
    String SHOP_ACTIVE = "com.ucamp.app.domain.auth.entity.ShopAccountStatus.ACTIVE";
    String TREATMENT_ACTIVE = "com.ucamp.app.domain.treatment.entity.TreatmentStatus.ACTIVE";
    String TREATMENT_NORMAL = "com.ucamp.app.domain.treatment.entity.TreatmentType.NORMAL";

    // 임박 빈자리는 사장님이 열지 않아도 영업시간 안 빈 시각을 보여주므로 OPEN 시간대 없이 매장 × 시술만 고른다
    String FIND_IMMINENT_CANDIDATES = """
            select new com.ucamp.app.domain.explore.repository.ExploreVacancyCandidate(
                   s.shopId, s.name, s.regionCode, s.address, s.latitude, s.longitude, s.imageUrl,
                   t.treatmentId, t.name, c.categoryId, c.name, t.treatmentType, t.durationMinutes,
                   t.regularPrice, t.price, t.lastMinutePrice, t.imageUrl)
            from Shop s
            join ShopAccount a on a.shopId = s.shopId
            join Treatment t on t.shop = s
            join ShopCategory c on c.categoryId = s.categoryId
            where a.status = \
            """
            + SHOP_ACTIVE
            + """
              and t.status = \
            """
            + TREATMENT_ACTIVE
            + """
              and t.treatmentType = \
            """
            + TREATMENT_NORMAL
            + """

            order by s.shopId, t.treatmentId
            """;

    String FIND_CANDIDATES = """
            select new com.ucamp.app.domain.explore.repository.ExploreVacancyCandidate(
                   e.startsAt, e.endsAt,
                   s.shopId, s.name, s.regionCode, s.address, s.latitude, s.longitude, s.imageUrl,
                   t.treatmentId, t.name, c.categoryId, c.name, t.treatmentType, t.durationMinutes,
                   t.regularPrice, t.price, t.lastMinutePrice, t.imageUrl)
            from ShopScheduleException e
            join Shop s on s.shopId = e.shopId
            join ShopAccount a on a.shopId = s.shopId
            join Treatment t on t.shop = s
            join ShopCategory c on c.categoryId = s.categoryId
            where e.exceptionType = \
            """
            + OPEN
            + """
              and a.status = \
            """
            + SHOP_ACTIVE
            + """
              and t.status = \
            """
            + TREATMENT_ACTIVE
            + """
              and e.endsAt > :from
              and e.startsAt < :to
              and (:allRegions = true or s.regionCode in :regionCodes)
              and (:categoryId is null or c.categoryId = :categoryId)
              and (:shopId is null or s.shopId = :shopId)
              and (:treatmentType is null or t.treatmentType = :treatmentType)
              and (:keyword is null
                   or upper(s.name) like :keyword escape '!'
                   or upper(t.name) like :keyword escape '!')
              and (:minLat is null
                   or (s.latitude between :minLat and :maxLat and s.longitude between :minLng and :maxLng))
            order by e.startsAt, t.treatmentId
            """;

    /**
     * 조건에 맞는 탐색 후보를 OPEN 시작 시각, 시술 ID 순으로 조회한다. 시간대가 조회 기간과 겹치면 후보로 본다(종료 시각 미포함).
     *
     * @param condition 조회 조건
     * @return 탐색 후보 목록
     */
    default List<ExploreVacancyCandidate> findCandidates(ExploreVacancyCondition condition) {
        boolean allRegions = condition.regionCodes().isEmpty();
        // 빈 목록을 IN 에 넘기지 않도록 전체 지역이면 쓰지 않는 값을 넣는다
        List<String> regionCodes = allRegions ? List.of("") : condition.regionCodes();
        String keyword = condition.keyword() == null
                ? null
                : "%" + escapeLike(condition.keyword().toUpperCase()) + "%";
        MapBounds bounds = condition.bounds();
        return findCandidates(condition.from(), condition.to(), allRegions, regionCodes, condition.categoryId(),
                condition.shopId(), condition.treatmentType(), keyword,
                bounds == null ? null : bounds.minLat(), bounds == null ? null : bounds.maxLat(),
                bounds == null ? null : bounds.minLng(), bounds == null ? null : bounds.maxLng());
    }

    @Query(FIND_CANDIDATES)
    List<ExploreVacancyCandidate> findCandidates(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("allRegions") boolean allRegions,
            @Param("regionCodes") List<String> regionCodes,
            @Param("categoryId") Long categoryId,
            @Param("shopId") Long shopId,
            @Param("treatmentType") TreatmentType treatmentType,
            @Param("keyword") String keyword,
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng);

    /**
     * 임박 빈자리 후보. 노출 중인 매장의 판매 중인 일반 시술(모델 시술 제외)을 매장 ID, 시술 ID 순으로 조회한다.
     *
     * @return 임박 빈자리 후보 목록 (openStartsAt·openEndsAt 은 null)
     */
    @Query(FIND_IMMINENT_CANDIDATES)
    List<ExploreVacancyCandidate> findImminentCandidates();

    // 검색어의 % · _ 가 와일드카드로 해석되지 않게 한다. 이스케이프 문자는 쿼리의 escape '!' 와 같다
    private static String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
