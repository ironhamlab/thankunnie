package com.ucamp.app.domain.schedule.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.converter.BooleanToYnConverter;
import com.ucamp.app.common.converter.LocalTimeToHhmmConverter;
import com.ucamp.app.domain.vacancy.policy.TimeRange;

/**
 * 요일별 영업시간 (매주 반복). 일회성 연장·휴무는 ShopScheduleException 이 담당한다.
 * 테이블에 CREATED_AT·UPDATED_AT 이 없어 BaseTimeEntity 를 상속하지 않는다.
 * 자정을 넘기는 영업은 지원하지 않는다 (종료 시각 &gt; 시작 시각).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "SHOP_BUSINESS_HOUR")
public class ShopBusinessHour {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_SHOP_BUSINESS_HOUR_GENERATOR")
    @SequenceGenerator(name = "SEQ_SHOP_BUSINESS_HOUR_GENERATOR",
            sequenceName = "SEQ_SHOP_BUSINESS_HOUR", allocationSize = 1)
    @Column(name = "BUSINESS_HOUR_ID")
    private Long id;

    @Column(name = "SHOP_ID", nullable = false)
    private Long shopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "DAY_OF_WEEK", nullable = false, length = 3)
    private BusinessDay dayOfWeek;

    @Convert(converter = LocalTimeToHhmmConverter.class)
    @Column(name = "OPEN_TIME", length = 5)
    private LocalTime openTime;

    @Convert(converter = LocalTimeToHhmmConverter.class)
    @Column(name = "CLOSE_TIME", length = 5)
    private LocalTime closeTime;

    @Convert(converter = BooleanToYnConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "IS_CLOSED", nullable = false, length = 1)
    private boolean closed;

    @Builder
    private ShopBusinessHour(Long shopId, BusinessDay dayOfWeek, boolean closed, LocalTime openTime,
            LocalTime closeTime) {
        this.shopId = shopId;
        this.dayOfWeek = dayOfWeek;
        change(closed, openTime, closeTime);
    }

    /** 휴무로 바꾸면 영업 시각은 지운다 */
    public void change(boolean closed, LocalTime openTime, LocalTime closeTime) {
        this.closed = closed;
        this.openTime = closed ? null : openTime;
        this.closeTime = closed ? null : closeTime;
    }

    /** 해당 날짜의 영업 구간. 휴무면 비어 있다 */
    public Optional<TimeRange> rangeOn(LocalDate date) {
        if (closed || openTime == null || closeTime == null) {
            return Optional.empty();
        }
        return Optional.of(new TimeRange(date.atTime(openTime), date.atTime(closeTime)));
    }
}
