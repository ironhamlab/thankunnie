package com.ucamp.app.domain.shop.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ucamp.app.domain.shop.entity.ShopCustomerMemo;

/**
 * 매장 고객 집계 조회. 예약·시술·회원은 각 담당 도메인 소유라 엔티티 연관 없이 네이티브 쿼리로 읽는다.
 * 이용 추정 = 확정 예약 중 종료 시각이 :now 이하이고 노쇼가 아닌 예약. 횟수·금액은 저장하지 않고 조회할 때마다 예약에서 계산한다.
 * 별칭은 대소문자를 지키도록 큰따옴표로 쓴다.
 */
public interface ShopCustomerRepository extends Repository<ShopCustomerMemo, Long> {

    /** 이 매장에 확정·취소 완료 예약이 있는 고객별 집계. 뒤에 조건을 더 붙인 뒤 GROUP_BY 로 닫는다 */
    String CUSTOMER_SELECT = """
            SELECT r.USER_ID AS "userId",
                   u.NAME AS "name",
                   u.PHONE AS "phone",
                   SUM(CASE WHEN r.STATUS = 'CONFIRMED' THEN 1 ELSE 0 END) AS "confirmedCount",
                   SUM(CASE WHEN r.STATUS = 'CONFIRMED' AND r.NO_SHOW = 'N' AND r.ENDS_AT <= :now THEN 1 ELSE 0 END) AS "visitCount",
                   SUM(CASE WHEN r.STATUS = 'CONFIRMED' AND r.NO_SHOW = 'N' AND r.ENDS_AT <= :now THEN r.APPLIED_PRICE ELSE 0 END)
                       AS "totalAmount",
                   MIN(CASE WHEN r.STATUS = 'CONFIRMED' AND r.NO_SHOW = 'N' AND r.ENDS_AT <= :now THEN r.STARTS_AT END) AS "firstVisitAt",
                   MAX(CASE WHEN r.STATUS = 'CONFIRMED' AND r.NO_SHOW = 'N' AND r.ENDS_AT <= :now THEN r.STARTS_AT END) AS "lastVisitAt",
                   MIN(CASE WHEN r.STATUS = 'CONFIRMED' AND r.ENDS_AT > :now THEN r.STARTS_AT END)
                       AS "nextReservationAt",
                   SUM(CASE WHEN r.STATUS = 'CONFIRMED' AND r.NO_SHOW = 'Y' THEN 1 ELSE 0 END) AS "noShowCount",
                   MAX(CASE WHEN m.MEMO_ID IS NULL THEN 0 ELSE 1 END) AS "hasMemo"
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            JOIN APP_USER u ON u.USER_ID = r.USER_ID
            LEFT JOIN SHOP_CUSTOMER_MEMO m ON m.SHOP_ID = t.SHOP_ID AND m.USER_ID = r.USER_ID
            WHERE t.SHOP_ID = :shopId
              AND r.STATUS IN ('CONFIRMED', 'CANCELLED')
            """;

    String GROUP_BY = """
             GROUP BY r.USER_ID, u.NAME, u.PHONE
            """;

    /** 목록 조건: 확정 예약이 있는 고객만, 이름·연락처 검색, 분류(이용 횟수 기준) */
    String LIST_FROM = """
            FROM (
            """ + CUSTOMER_SELECT + """
               AND (u.NAME LIKE :pattern ESCAPE '\\' OR u.PHONE LIKE :pattern ESCAPE '\\')
            """ + GROUP_BY + """
            ) g
            WHERE g."confirmedCount" > 0
              AND (:segment = 'ALL'
                   OR (:segment = 'NEW' AND g."visitCount" < :returningMinVisits)
                   OR (:segment = 'RETURNING' AND g."visitCount" >= :returningMinVisits))
            """;

    @Query(value = """
            SELECT g."userId" AS "userId", g."name" AS "name", g."phone" AS "phone",
                   g."visitCount" AS "visitCount", g."totalAmount" AS "totalAmount",
                   g."firstVisitAt" AS "firstVisitAt", g."lastVisitAt" AS "lastVisitAt",
                   g."nextReservationAt" AS "nextReservationAt", g."noShowCount" AS "noShowCount",
                   g."hasMemo" AS "hasMemo"
            """ + LIST_FROM + """
            ORDER BY CASE WHEN :sort = 'VISITS' THEN g."visitCount" END DESC NULLS LAST,
                     CASE WHEN :sort = 'AMOUNT' THEN g."totalAmount" END DESC NULLS LAST,
                     g."lastVisitAt" DESC NULLS LAST,
                     g."nextReservationAt" ASC NULLS LAST,
                     g."userId"
            OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """, nativeQuery = true)
    List<CustomerRow> findCustomers(@Param("shopId") Long shopId, @Param("now") LocalDateTime now,
            @Param("pattern") String pattern, @Param("segment") String segment,
            @Param("returningMinVisits") int returningMinVisits, @Param("sort") String sort,
            @Param("offset") long offset, @Param("size") int size);

    @Query(value = "SELECT COUNT(*) " + LIST_FROM, nativeQuery = true)
    Number countCustomersRaw(@Param("shopId") Long shopId, @Param("now") LocalDateTime now,
            @Param("pattern") String pattern, @Param("segment") String segment,
            @Param("returningMinVisits") int returningMinVisits);

    default long countCustomers(Long shopId, LocalDateTime now, String pattern, String segment,
            int returningMinVisits) {
        Number count = countCustomersRaw(shopId, now, pattern, segment, returningMinVisits);
        return count == null ? 0 : count.longValue();
    }

    /** 한 고객의 집계. 이 매장에 확정·취소 완료 예약이 없으면 비어 있다 */
    @Query(value = CUSTOMER_SELECT + """
               AND r.USER_ID = :userId
            """ + GROUP_BY, nativeQuery = true)
    Optional<CustomerRow> findCustomer(@Param("shopId") Long shopId, @Param("userId") Long userId,
            @Param("now") LocalDateTime now);

    /** 한 고객의 이 매장 확정·취소 완료 예약. 최근 시작 순으로 최대 50건 */
    @Query(value = """
            SELECT r.RESERVATION_ID AS "reservationId",
                   r.STARTS_AT AS "startsAt",
                   r.ENDS_AT AS "endsAt",
                   t.NAME AS "treatmentName",
                   r.APPLIED_PRICE AS "appliedPrice",
                   r.STATUS AS "status",
                   c.CANCEL_TYPE AS "cancelType",
                   r.CUSTOMER_REQUEST AS "customerRequest",
                   CASE WHEN r.NO_SHOW = 'Y' THEN 1 ELSE 0 END AS "noShow"
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            LEFT JOIN RESERVATION_CANCEL c ON c.RESERVATION_ID = r.RESERVATION_ID
            WHERE t.SHOP_ID = :shopId
              AND r.USER_ID = :userId
              AND r.STATUS IN ('CONFIRMED', 'CANCELLED')
            ORDER BY r.STARTS_AT DESC, r.RESERVATION_ID DESC
            FETCH FIRST 50 ROWS ONLY
            """, nativeQuery = true)
    List<HistoryRow> findHistory(@Param("shopId") Long shopId, @Param("userId") Long userId);

    /** 이 매장에 확정·취소 완료 예약이 있는 고객인지. 메모는 이런 고객에게만 남길 수 있다 */
    @Query(value = """
            SELECT COUNT(*)
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            WHERE t.SHOP_ID = :shopId
              AND r.USER_ID = :userId
              AND r.STATUS IN ('CONFIRMED', 'CANCELLED')
            """, nativeQuery = true)
    Number countReservationsRaw(@Param("shopId") Long shopId, @Param("userId") Long userId);

    default boolean isCustomer(Long shopId, Long userId) {
        Number count = countReservationsRaw(shopId, userId);
        return count != null && count.longValue() > 0;
    }

    interface CustomerRow {

        Long getUserId();

        String getName();

        String getPhone();

        Long getVisitCount();

        Long getTotalAmount();

        LocalDateTime getFirstVisitAt();

        LocalDateTime getLastVisitAt();

        LocalDateTime getNextReservationAt();

        Long getNoShowCount();

        Long getHasMemo();
    }

    interface HistoryRow {

        Long getReservationId();

        LocalDateTime getStartsAt();

        LocalDateTime getEndsAt();

        String getTreatmentName();

        Long getAppliedPrice();

        String getStatus();

        String getCancelType();

        String getCustomerRequest();

        Long getNoShow();
    }
}
