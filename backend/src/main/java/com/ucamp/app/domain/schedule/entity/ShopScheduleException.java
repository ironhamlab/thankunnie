package com.ucamp.app.domain.schedule.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.entity.BaseTimeEntity;

/**
 * 시간대 열기·닫기(예외 일정). 매주 반복 영업시간과 다른 일회성 연장 영업·휴무를 담는다.
 * 매장·예약은 각 담당 엔티티를 기다리지 않도록 ID 로만 참조한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "SHOP_SCHEDULE_EXCEPTION")
public class ShopScheduleException extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_SHOP_SCHEDULE_EXCEPTION_GENERATOR")
    @SequenceGenerator(name = "SEQ_SHOP_SCHEDULE_EXCEPTION_GENERATOR",
            sequenceName = "SEQ_SHOP_SCHEDULE_EXCEPTION", allocationSize = 1)
    @Column(name = "EXCEPTION_ID")
    private Long id;

    @Column(name = "SHOP_ID", nullable = false)
    private Long shopId;

    /** 매장이 예약을 취소하며 그 시간을 닫은 경우에만 값이 있다 */
    @Column(name = "RESERVATION_ID")
    private Long reservationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "EXCEPTION_TYPE", nullable = false, length = 10)
    private ScheduleExceptionType exceptionType;

    @Column(name = "STARTS_AT", nullable = false)
    private LocalDateTime startsAt;

    /** 종료 시각은 구간에 포함하지 않는다 */
    @Column(name = "ENDS_AT", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "REASON", length = 500)
    private String reason;

    @Builder
    private ShopScheduleException(Long shopId, Long reservationId, ScheduleExceptionType exceptionType,
            LocalDateTime startsAt, LocalDateTime endsAt, String reason) {
        this.shopId = shopId;
        this.reservationId = reservationId;
        this.exceptionType = exceptionType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
    }

    public void change(ScheduleExceptionType exceptionType, LocalDateTime startsAt, LocalDateTime endsAt,
            String reason) {
        this.exceptionType = exceptionType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
    }
}
