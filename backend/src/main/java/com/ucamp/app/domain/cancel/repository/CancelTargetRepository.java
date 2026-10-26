package com.ucamp.app.domain.cancel.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ucamp.app.domain.reservation.entity.ReservationStatus;

/**
 * 취소 대상 조회. 예약(D)·결제(C) 엔티티에 의존하지 않도록 예약 → 시술(매장), 결제를 SQL 로 한 번에 읽는다.
 * 결제는 결제 완료(PAID) 건만 본다. 결제 취소 실패로 확정 상태로 되돌린 예약은 결제도 PAID 로 돌아와 있어야 한다.
 * 한 예약에 결제 완료가 여러 건이면 이중 결제 사고다. 하나를 골라 견적을 내면 환불 누락을 숨기게 되므로 예외로 막는다.
 */
@Repository
public class CancelTargetRepository {

    private static final String FIND_SQL = """
            SELECT r.RESERVATION_ID, r.USER_ID, t.SHOP_ID, r.STATUS, r.STARTS_AT, r.CREATED_AT,
                   p.AMOUNT, p.APPROVED_AT
            FROM RESERVATION r
            JOIN TREATMENT t ON t.TREATMENT_ID = r.TREATMENT_ID
            LEFT JOIN DEPOSIT_PAYMENT p ON p.RESERVATION_ID = r.RESERVATION_ID AND p.STATUS = 'PAID'
            WHERE r.RESERVATION_ID = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public CancelTargetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CancelTarget> findByReservationId(Long reservationId) {
        List<CancelTarget> rows = jdbcTemplate.query(FIND_SQL, this::mapRow, reservationId);
        if (rows.size() > 1) {
            throw new IllegalStateException("예약 " + reservationId + " 에 결제 완료(PAID) 건이 " + rows.size() + "건 있습니다");
        }
        return rows.stream().findFirst();
    }

    private CancelTarget mapRow(ResultSet rs, int rowNum) throws SQLException {
        int amount = rs.getInt("AMOUNT");
        Integer paidAmount = rs.wasNull() ? null : amount;
        return new CancelTarget(
                rs.getLong("RESERVATION_ID"),
                rs.getLong("USER_ID"),
                rs.getLong("SHOP_ID"),
                ReservationStatus.valueOf(rs.getString("STATUS")),
                toLocal(rs.getTimestamp("STARTS_AT")),
                toLocal(rs.getTimestamp("CREATED_AT")),
                paidAmount,
                toLocal(rs.getTimestamp("APPROVED_AT")));
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
