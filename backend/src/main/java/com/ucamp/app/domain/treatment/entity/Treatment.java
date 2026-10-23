package com.ucamp.app.domain.treatment.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.entity.BaseTimeEntity;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.entity.Shop;
import com.ucamp.app.domain.shop.entity.ShopCategory;
import com.ucamp.app.domain.treatment.dto.request.TreatmentSaveRequest;

/** 사장님 매장의 시술 메뉴. 기존 예약의 가격·시각 스냅샷은 변경하지 않는다. */
@Entity
@Table(name = "TREATMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Treatment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_treatment")
    @SequenceGenerator(name = "seq_treatment", sequenceName = "SEQ_TREATMENT", allocationSize = 1)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "TREATMENT_ID")
    private Long treatmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SHOP_ID", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CATEGORY_ID", nullable = false)
    private ShopCategory category;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "STAFF_ID")
    private Long staffId;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "DESCRIPTION", length = 2000)
    private String description;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "REGULAR_PRICE", nullable = false)
    private Integer regularPrice;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "PRICE", nullable = false)
    private Integer price;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "LAST_MINUTE_PRICE", nullable = false)
    private Integer lastMinutePrice;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "DURATION_MINUTES", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "TREATMENT_TYPE", nullable = false, length = 20)
    private TreatmentType treatmentType;

    @Column(name = "MODEL_CONDITION", length = 2000)
    private String modelCondition;

    @Column(name = "PREPARATION_NOTES", length = 1000)
    private String preparationNotes;

    @Column(name = "PHOTO_NOTICE", length = 1000)
    private String photoNotice;

    @Column(name = "IMAGE_URL", length = 1000)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private TreatmentStatus status;

    public static Treatment create(Shop shop, ShopCategory category, TreatmentSaveRequest request) {
        Treatment treatment = new Treatment();
        treatment.shop = shop;
        treatment.regularPrice = request.regularPrice();
        treatment.price = request.regularPrice();
        treatment.lastMinutePrice = request.regularPrice();
        treatment.treatmentType = TreatmentType.NORMAL;
        treatment.status = TreatmentStatus.ACTIVE;
        treatment.updateMenu(category, request);
        return treatment;
    }

    public void updateMenu(ShopCategory category, TreatmentSaveRequest request) {
        // 할인 미설정 가격은 정상가를 따라가고, 별도 설정된 할인가는 보존한다.
        boolean followsRegularPrice = price.equals(regularPrice);
        boolean lastMinuteFollowsRegularPrice = lastMinutePrice.equals(regularPrice);
        if (!followsRegularPrice && price > request.regularPrice()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정상가는 설정된 판매가 이상이어야 합니다.");
        }
        int nextPrice = followsRegularPrice ? request.regularPrice() : price;
        if (!lastMinuteFollowsRegularPrice && lastMinutePrice > nextPrice) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정상가 변경 후 판매가는 설정된 초임박가 이상이어야 합니다.");
        }
        if (followsRegularPrice) {
            price = request.regularPrice();
        }
        if (lastMinuteFollowsRegularPrice) {
            lastMinutePrice = request.regularPrice();
        }
        this.category = category;
        this.name = request.name().strip();
        this.description = request.description();
        this.regularPrice = request.regularPrice();
        this.durationMinutes = request.durationMinutes();
    }

    public void changeStatus(TreatmentStatus status) {
        this.status = status;
    }
}
