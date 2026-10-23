package com.ucamp.app.domain.reservation.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.entity.BaseTimeEntity;
import com.ucamp.app.common.policy.ReservationStatus;
import com.ucamp.app.domain.treatment.entity.Treatment;

/**
 * 예약. 고객이 예약을 클릭할 때 점유(HOLD) 상태로 생성되며, 가격·예약금은 클릭 시점 값으로 고정한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "RESERVATION")
public class Reservation extends BaseTimeEntity {

    // 숫자 컬럼은 DDL 의 NUMBER(p) 와 맞춘다. H2(Oracle 모드)는 NUMBER 를 NUMERIC 으로 보고해
    // 기본 매핑(BIGINT, INTEGER)이면 ddl-auto: validate 가 실패한다
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_RESERVATION")
    @SequenceGenerator(name = "SEQ_RESERVATION", sequenceName = "SEQ_RESERVATION", allocationSize = 1)
    @Column(name = "RESERVATION_ID")
    private Long id;

    // 회원 엔티티가 생기기 전까지 ID 로만 참조한다
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TREATMENT_ID", nullable = false)
    private Treatment treatment;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "STARTS_AT", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ENDS_AT", nullable = false)
    private LocalDateTime endsAt;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "APPLIED_PRICE", nullable = false)
    private int appliedPrice;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "DEPOSIT", nullable = false)
    private int deposit;

    @Column(name = "HOLD_EXPIRES_AT", nullable = false)
    private LocalDateTime holdExpiresAt;

    @Column(name = "CONFIRMED_AT")
    private LocalDateTime confirmedAt;

    @Column(name = "CUSTOMER_REQUEST", length = 1000)
    private String customerRequest;

    @Builder
    private Reservation(Long userId, Treatment treatment, LocalDateTime startsAt, LocalDateTime endsAt,
            int appliedPrice, int deposit, LocalDateTime holdExpiresAt, String customerRequest) {
        this.userId = userId;
        this.treatment = treatment;
        this.status = ReservationStatus.HOLD;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.appliedPrice = appliedPrice;
        this.deposit = deposit;
        this.holdExpiresAt = holdExpiresAt;
        this.customerRequest = customerRequest;
    }
}
