package com.ucamp.app.domain.notification.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.common.policy.BookingPolicy;

/**
 * 알림 대상 빈자리 조회. 예외 일정·예약·시술·매장은 다른 도메인 소유라 엔티티 없이 읽기 전용 SQL 로 조회한다.
 * 빈자리는 예외 일정 OPEN 행이며, 겹치는 CLOSE 가 있으면 CLOSE 가 우선한다.
 */
@Repository
@RequiredArgsConstructor
public class VacancySlotRepository {

    // OPEN 행 전체를 CLOSE 가 덮으면 판매할 시간이 없다
    private static final String NOT_FULLY_CLOSED = """
            NOT EXISTS (
                SELECT 1 FROM SHOP_SCHEDULE_EXCEPTION c
                WHERE c.SHOP_ID = o.SHOP_ID
                  AND c.EXCEPTION_TYPE = 'CLOSE'
                  AND c.STARTS_AT <= o.STARTS_AT
                  AND c.ENDS_AT >= o.ENDS_AT)
            """;

    private static final String FIND_IMMINENT_SQL = """
            SELECT o.EXCEPTION_ID, o.SHOP_ID, o.STARTS_AT, o.ENDS_AT
            FROM SHOP_SCHEDULE_EXCEPTION o
            JOIN SHOP s ON s.SHOP_ID = o.SHOP_ID
            WHERE o.EXCEPTION_TYPE = 'OPEN'
              AND s.STATUS = 'ACTIVE'
              AND o.STARTS_AT <= :imminentUntil
              AND o.ENDS_AT > :bookableEndsAfter
              AND %s
              AND NOT EXISTS (
                  SELECT 1 FROM NOTIFICATION_EVENT e
                  WHERE e.EXCEPTION_ID = o.EXCEPTION_ID
                    AND e.EVENT_TYPE = 'IMMINENT')
            ORDER BY o.STARTS_AT, o.EXCEPTION_ID
            """.formatted(NOT_FULLY_CLOSED);

    // 점유 판단은 SalePolicy.isOccupying 과 같은 조건이다
    private static final String FIND_REOPENED_SQL = """
            SELECT o.EXCEPTION_ID, t.SHOP_ID, r.RESERVATION_ID, r.STARTS_AT, r.ENDS_AT
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            JOIN SHOP s ON s.SHOP_ID = t.SHOP_ID
            JOIN SHOP_SCHEDULE_EXCEPTION o
              ON o.SHOP_ID = t.SHOP_ID
             AND o.EXCEPTION_TYPE = 'OPEN'
             AND o.STARTS_AT <= r.STARTS_AT
             AND o.ENDS_AT >= r.ENDS_AT
            WHERE r.RESERVATION_ID = :reservationId
              AND r.STATUS = 'CANCELLED'
              AND t.TREATMENT_TYPE = 'NORMAL'
              AND s.STATUS = 'ACTIVE'
              AND r.STARTS_AT > :bookingClosesBefore
              AND NOT EXISTS (
                  SELECT 1 FROM SHOP_SCHEDULE_EXCEPTION c
                  WHERE c.SHOP_ID = t.SHOP_ID
                    AND c.EXCEPTION_TYPE = 'CLOSE'
                    AND c.STARTS_AT < r.ENDS_AT
                    AND r.STARTS_AT < c.ENDS_AT)
              AND NOT EXISTS (
                  SELECT 1 FROM RESERVATION x
                  JOIN TREATMENT xt ON xt.TREATMENT_ID = x.TREATMENT_ID
                  WHERE xt.SHOP_ID = t.SHOP_ID
                    AND x.RESERVATION_ID <> r.RESERVATION_ID
                    AND x.STARTS_AT < r.ENDS_AT
                    AND r.STARTS_AT < x.ENDS_AT
                    AND (x.STATUS IN ('CONFIRMED', 'CANCEL_PENDING')
                         OR (x.STATUS = 'HOLD' AND x.HOLD_EXPIRES_AT > :now)))
            ORDER BY o.STARTS_AT, o.EXCEPTION_ID
            FETCH FIRST 1 ROWS ONLY
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 임박 구간에 들어왔고 아직 임박 이벤트가 없는 빈자리를 조회한다.
     * 마지막 시작 가능 시각(종료 30분 전)의 예약 접수가 끝난 빈자리는 제외한다.
     *
     * @param now 현재 시각 (서울)
     * @return 임박 이벤트를 만들 빈자리 목록
     */
    public List<VacancySlot> findImminentWithoutEvent(LocalDateTime now) {
        Map<String, Object> params = Map.of(
                "imminentUntil", now.plus(BookingPolicy.IMMINENT_FROM),
                "bookableEndsAfter", now.plus(BookingPolicy.BOOKING_DEADLINE)
                        .plusMinutes(BookingPolicy.TIME_UNIT_MINUTES));
        return jdbcTemplate.query(FIND_IMMINENT_SQL, params, (rs, rowNum) -> new VacancySlot(
                rs.getLong("EXCEPTION_ID"),
                rs.getLong("SHOP_ID"),
                null,
                toLocalDateTime(rs, "STARTS_AT"),
                toLocalDateTime(rs, "ENDS_AT")));
    }

    /**
     * 취소된 예약 시간이 실제로 다시 판매 가능한지 확인하고, 그 시간을 담은 빈자리를 조회한다.
     * 취소 완료, 일반 시술, 접수 마감 전, 겹치는 CLOSE 없음, 다른 예약이 잡고 있지 않음을 모두 만족해야 한다.
     *
     * @param reservationId 취소된 예약 ID
     * @param now           현재 시각 (서울)
     * @return 재공개된 빈자리. 판매 가능하지 않으면 empty
     */
    public Optional<VacancySlot> findReopened(Long reservationId, LocalDateTime now) {
        Map<String, Object> params = Map.of(
                "reservationId", reservationId,
                "bookingClosesBefore", now.plus(BookingPolicy.BOOKING_DEADLINE),
                "now", now);
        return jdbcTemplate.query(FIND_REOPENED_SQL, params, (rs, rowNum) -> new VacancySlot(
                        rs.getLong("EXCEPTION_ID"),
                        rs.getLong("SHOP_ID"),
                        rs.getLong("RESERVATION_ID"),
                        toLocalDateTime(rs, "STARTS_AT"),
                        toLocalDateTime(rs, "ENDS_AT")))
                .stream()
                .findFirst();
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }
}
