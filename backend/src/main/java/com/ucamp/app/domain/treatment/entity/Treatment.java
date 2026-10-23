package com.ucamp.app.domain.treatment.entity;

import jakarta.persistence.Column;
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

import com.ucamp.app.common.entity.BaseTimeEntity;
import com.ucamp.app.domain.vacancy.policy.SalePrices;

/**
 * 시술. 예약 생성에 필요한 컬럼(매장, 가격 3종, 소요 시간, 상태)만 매핑한다.
 * 시술 등록·수정 기능에서 나머지 컬럼과 상태 변경 메서드를 추가한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "TREATMENT")
public class Treatment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_TREATMENT")
    @SequenceGenerator(name = "SEQ_TREATMENT", sequenceName = "SEQ_TREATMENT", allocationSize = 1)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "TREATMENT_ID")
    private Long id;

    // 매장 엔티티가 생기기 전까지 ID 로만 참조한다
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SHOP_ID", nullable = false)
    private Long shopId;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "REGULAR_PRICE", nullable = false)
    private int regularPrice;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "PRICE", nullable = false)
    private int price;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "LAST_MINUTE_PRICE", nullable = false)
    private int lastMinutePrice;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "DURATION_MINUTES", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private TreatmentStatus status;

    @Builder
    private Treatment(Long id, Long shopId, String name, int regularPrice, int price, int lastMinutePrice,
            int durationMinutes, TreatmentStatus status) {
        this.id = id;
        this.shopId = shopId;
        this.name = name;
        this.regularPrice = regularPrice;
        this.price = price;
        this.lastMinutePrice = lastMinutePrice;
        this.durationMinutes = durationMinutes;
        this.status = status;
    }

    public boolean isActive() {
        return status == TreatmentStatus.ACTIVE;
    }

    /**
     * 가격 계산(PriceCalculator)에 넘길 구간별 판매가.
     */
    public SalePrices salePrices() {
        return new SalePrices(price, lastMinutePrice);
    }
}
