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
 * 알림을 만들 빈자리를 조회한다.
 * 예외 일정, 예약, 시술, 매장은 다른 도메인 테이블이라 엔티티 없이 SQL로 읽기만 한다.
 * 빈자리는 예외 일정 OPEN 행이다.
 */
@Repository
@RequiredArgsConstructor
public class VacancySlotRepository {

    // OPEN 전체를 CLOSE가 덮으면 팔 시간이 없다
    private static final String NOT_FULLY_CLOSED = """
        NOT EXISTS (
                SELECT 1 FROM SHOP_SCHEDULE_EXCEPTION c
                WHERE c.SHOP_ID = o.SHOP_ID
                AND c.EXCEPTION_TYPE = 'CLOSE'
                AND c.STARTS_AT <= o.STARTS_AT
                AND c.ENDS_AT >= o.ENDS_AT)""";

    // 확정, 취소 처리 중, 만료 전이거나 만료 시각이 없는 점유가 겹치면 제외한다
    private static final String NOT_OCCUPIED = """
        NOT EXISTS (
                SELECT 1 FROM RESERVATION x
                JOIN TREATMENT xt ON xt.TREATMENT_ID = x.TREATMENT_ID
                WHERE xt.SHOP_ID = o.SHOP_ID
                AND x.STARTS_AT < o.ENDS_AT
                AND o.STARTS_AT < x.ENDS_AT
                AND (x.STATUS IN ('CONFIRMED', 'CANCEL_PENDING')
                OR (x.STATUS = 'HOLD'
                AND (x.HOLD_EXPIRES_AT IS NULL OR x.HOLD_EXPIRES_AT > :now))))""";

    // 같은 매장·시작 시각의 임박 이벤트는 아직 없어야 한다 (UK_NOTIFICATION_EVENT_SLOT 과 동일)
    private static final String NOT_ALREADY_IMMINENT = """
        NOT EXISTS (
                SELECT 1 FROM NOTIFICATION_EVENT e
                WHERE e.SHOP_ID = o.SHOP_ID
                AND e.EVENT_TYPE = 'IMMINENT'
                AND e.SLOT_STARTS_AT = o.STARTS_AT)""";

    // 같은 매장·시작 시각의 초임박 이벤트는 아직 없어야 한다 (UK_NOTIFICATION_EVENT_SLOT 과 동일)
    private static final String NOT_ALREADY_LAST_MINUTE = """
        NOT EXISTS (
                SELECT 1 FROM NOTIFICATION_EVENT e
                WHERE e.SHOP_ID = o.SHOP_ID
                AND e.EVENT_TYPE = 'LAST_MINUTE'
                AND e.SLOT_STARTS_AT = o.STARTS_AT)""";

    // 영업 중인 매장의 OPEN. 시작이 8시간 이내이고 3시간보다 많이 남았으며, 아직 클릭할 수 있는 것만
    private static final String FIND_IMMINENT_SQL = """
        SELECT o.EXCEPTION_ID, o.SHOP_ID, o.STARTS_AT, o.ENDS_AT
        FROM SHOP_SCHEDULE_EXCEPTION o
        JOIN SHOP s ON s.SHOP_ID = o.SHOP_ID
        WHERE o.EXCEPTION_TYPE = 'OPEN'
        AND s.STATUS = 'ACTIVE'
        AND o.STARTS_AT <= :imminentUntil
        AND o.STARTS_AT > :lastMinuteUntil
        AND o.ENDS_AT > :bookableEndsAfter
        AND
        """
        + NOT_FULLY_CLOSED
        + """
        AND
        """
        + NOT_OCCUPIED
        + """
        AND
        """
        + NOT_ALREADY_IMMINENT
        + """
        ORDER BY o.STARTS_AT, o.EXCEPTION_ID
        """;

    // 영업 중인 매장의 OPEN. 시작이 3시간 이내이고, 아직 클릭할 수 있는 것만
    private static final String FIND_LAST_MINUTE_SQL = """
        SELECT o.EXCEPTION_ID, o.SHOP_ID, o.STARTS_AT, o.ENDS_AT
        FROM SHOP_SCHEDULE_EXCEPTION o
        JOIN SHOP s ON s.SHOP_ID = o.SHOP_ID
        WHERE o.EXCEPTION_TYPE = 'OPEN'
        AND s.STATUS = 'ACTIVE'
        AND o.STARTS_AT <= :lastMinuteUntil
        AND o.ENDS_AT > :bookableEndsAfter
        AND
        """
        + NOT_FULLY_CLOSED
        + """
        AND
        """
        + NOT_OCCUPIED
        + """
        AND
        """
        + NOT_ALREADY_LAST_MINUTE
        + """
        ORDER BY o.STARTS_AT, o.EXCEPTION_ID
        """;

    // 취소 완료된 일반 시술 예약만 읽는다. OPEN 행은 보지 않는다. 팔리는지는 isAvailable 이 본다.
    private static final String FIND_CANCELLED_RESERVATION_SQL = """
        SELECT t.SHOP_ID, r.RESERVATION_ID, r.STARTS_AT, r.ENDS_AT
        FROM RESERVATION r
        JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
        JOIN SHOP s ON s.SHOP_ID = t.SHOP_ID
        WHERE r.RESERVATION_ID = :reservationId
        AND r.STATUS = 'CANCELLED'
        AND t.TREATMENT_TYPE = 'NORMAL'
        AND s.STATUS = 'ACTIVE'
        """;

    // 이미 이벤트가 있는 빈자리도 다시 본다. 영업 중이고, 아직 클릭할 수 있고, 휴무·점유가 없을 것
    private static final String IS_OPEN_SLOT_SELLABLE_SQL = """
        SELECT o.EXCEPTION_ID
        FROM SHOP_SCHEDULE_EXCEPTION o
        JOIN SHOP s ON s.SHOP_ID = o.SHOP_ID
        WHERE o.EXCEPTION_ID = :exceptionId
        AND o.EXCEPTION_TYPE = 'OPEN'
        AND s.STATUS = 'ACTIVE'
        AND o.ENDS_AT > :bookableEndsAfter
        AND
        """
        + NOT_FULLY_CLOSED
        + """
        AND
        """
        + NOT_OCCUPIED;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 임박 구간(시작 8시간 전부터 3시간 초과 전까지)에 들어왔고, 아직 임박 이벤트가 없는 빈자리를 조회한다.
     * 시작까지 3시간 이내면 초임박 조회가 담당하므로 여기서는 빼 둔다.
     * OPEN 전체를 덮는 CLOSE가 있거나, 겹치는 예약이 시간을 잡고 있으면 제외한다.
     * 잡고 있는 예약은 확정, 취소 처리 중, 만료 전이거나 만료 시각이 없는 점유다.
     * 겹침은 종료 시각을 포함하지 않는다. 12:00~13:00과 13:00~14:00은 겹치지 않는다.
     * 마지막 시작 시각은 종료 30분 전이고, 그 시각의 클릭 마감(20분 전)이 지난 빈자리도 제외한다.
     *
     * @param now 현재 시각 (서울)
     * @return 임박 이벤트를 만들 빈자리 목록
     */
    public List<VacancySlot> findImminentWithoutEvent(LocalDateTime now) {
        LocalDateTime imminentUntil = now.plus(BookingPolicy.IMMINENT_FROM);
        LocalDateTime lastMinuteUntil = now.plus(BookingPolicy.LAST_MINUTE_FROM);
        LocalDateTime bookableEndsAfter = now.plus(BookingPolicy.BOOKING_DEADLINE)
                .plusMinutes(BookingPolicy.TIME_UNIT_MINUTES);
        Map<String, Object> params = Map.of(
                "imminentUntil", imminentUntil,
                "lastMinuteUntil", lastMinuteUntil,
                "bookableEndsAfter", bookableEndsAfter,
                "now", now);
        return jdbcTemplate.query(FIND_IMMINENT_SQL, params, VacancySlotRepository::toOpenSlot);
    }

    /**
     * 초임박 구간(시작 3시간 전)에 들어왔고, 아직 초임박 이벤트가 없는 빈자리를 조회한다.
     * 정확히 3시간 전도 초임박이다. 임박 이벤트가 이미 있어도 초임박 조회에서는 빼지 않는다.
     * OPEN 전체를 덮는 CLOSE가 있거나, 겹치는 예약이 시간을 잡고 있으면 제외한다.
     * 잡고 있는 예약과 겹침, 클릭 마감 기준은 임박 조회와 같다.
     *
     * @param now 현재 시각 (서울)
     * @return 초임박 이벤트를 만들 빈자리 목록
     */
    public List<VacancySlot> findLastMinuteWithoutEvent(LocalDateTime now) {
        LocalDateTime lastMinuteUntil = now.plus(BookingPolicy.LAST_MINUTE_FROM);
        LocalDateTime bookableEndsAfter = now.plus(BookingPolicy.BOOKING_DEADLINE)
                .plusMinutes(BookingPolicy.TIME_UNIT_MINUTES);
        Map<String, Object> params = Map.of(
                "lastMinuteUntil", lastMinuteUntil,
                "bookableEndsAfter", bookableEndsAfter,
                "now", now);
        return jdbcTemplate.query(FIND_LAST_MINUTE_SQL, params, VacancySlotRepository::toOpenSlot);
    }

    /**
     * 이 OPEN 빈자리가 아직 판매 가능한지 확인한다.
     * 매장이 영업 중이고, 클릭 마감이 지나지 않았으며, OPEN 전체를 덮는 CLOSE와 겹치는 점유가 없어야 한다.
     * 알림 이벤트가 이미 있어도 여기서는 빼지 않는다. 발송 직전에 빈자리만 다시 보는 조회다.
     *
     * @param exceptionId OPEN 예외 일정 ID
     * @param now         현재 시각 (서울)
     * @return 아직 팔 수 있으면 true
     */
    public boolean isOpenSlotSellable(Long exceptionId, LocalDateTime now) {
        if (exceptionId == null || now == null) {
            return false;
        }
        LocalDateTime bookableEndsAfter = now.plus(BookingPolicy.BOOKING_DEADLINE)
                .plusMinutes(BookingPolicy.TIME_UNIT_MINUTES);
        Map<String, Object> params = Map.of(
                "exceptionId", exceptionId,
                "bookableEndsAfter", bookableEndsAfter,
                "now", now);
        List<Long> found = jdbcTemplate.query(
                IS_OPEN_SLOT_SELLABLE_SQL, params, (rs, rowNum) -> rs.getLong("EXCEPTION_ID"));
        return !found.isEmpty();
    }

    /**
     * 취소가 끝난 일반 시술 예약을 읽는다.
     * OPEN 행은 보지 않는다. 그 시간이 지금 예약 가능한지는 AvailabilityService 가 본다.
     *
     * @param reservationId 취소된 예약 번호
     * @return 매장·시작·끝. 취소 완료·일반 시술·영업 중 매장이 아니면 빈 값
     */
    public Optional<VacancySlot> findCancelledReservation(Long reservationId) {
        if (reservationId == null) {
            return Optional.empty();
        }
        List<VacancySlot> slots = jdbcTemplate.query(
                FIND_CANCELLED_RESERVATION_SQL,
                Map.of("reservationId", reservationId),
                VacancySlotRepository::toCancelledSlot);
        if (slots.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(slots.get(0));
    }

    // query가 (결과 행, 행 번호)를 넘긴다. 행 번호는 쓰지 않는다.
    // 임박·초임박은 예약이 없다. 시각은 OPEN의 시작·종료다.
    private static VacancySlot toOpenSlot(ResultSet rs, int rowNum) throws SQLException {
        return new VacancySlot(
                rs.getLong("EXCEPTION_ID"),
                rs.getLong("SHOP_ID"),
                null,
                toLocalDateTime(rs, "STARTS_AT"),
                toLocalDateTime(rs, "ENDS_AT"));
    }

    // OPEN 없이 읽은 취소 예약. exceptionId 는 비운다.
    private static VacancySlot toCancelledSlot(ResultSet rs, int rowNum) throws SQLException {
        return new VacancySlot(
                null,
                rs.getLong("SHOP_ID"),
                rs.getLong("RESERVATION_ID"),
                toLocalDateTime(rs, "STARTS_AT"),
                toLocalDateTime(rs, "ENDS_AT"));
    }

    // 시각 컬럼을 LocalDateTime으로 읽는다.
    private static LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }
}
