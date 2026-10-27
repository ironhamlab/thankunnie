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

import org.hibernate.annotations.DynamicUpdate;
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
import com.ucamp.app.domain.treatment.dto.request.TreatmentConditionsRequest;
import com.ucamp.app.domain.vacancy.policy.SalePrices;

/** 사장님 매장의 시술 메뉴. 기존 예약의 가격·시각 스냅샷은 변경하지 않는다. */
@Entity
@DynamicUpdate
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

        treatment.treatmentType = TreatmentType.NORMAL;
        treatment.status = TreatmentStatus.ACTIVE;
        treatment.updateMenu(category, request);
        return treatment;
    }

    public void updateMenu(ShopCategory category, TreatmentSaveRequest request) {
        new SalePrices(request.price(), request.lastMinutePrice()).validateFor(request.regularPrice());
        if (request.conditions() != null) {
            updateConditions(request.conditions());
        }
        this.price = request.price();
        this.lastMinutePrice = request.lastMinutePrice();
        this.category = category;
        this.name = request.name().strip();
        this.description = request.description();
        this.regularPrice = request.regularPrice();
        this.durationMinutes = request.durationMinutes();
    }

    /** 목적·모델 조건을 함께 변경하며 일반 시술로 전환하면 기존 모델 정보를 비운다. */
    private void updateConditions(TreatmentConditionsRequest conditions) {
        if (conditions.treatmentType() == null || !conditions.isModelConditionsValid()
                || !conditions.isNormalConditionsValid()
                || conditions.modelCondition() != null && conditions.modelCondition().length() > 2000
                || conditions.photoNotice() != null && conditions.photoNotice().length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "목적과 모델 모집 조건을 확인해 주세요.");
        }
        this.treatmentType = conditions.treatmentType();
        this.modelCondition = conditions.treatmentType() == TreatmentType.MODEL
                ? conditions.modelCondition().strip() : null;
        this.photoNotice = conditions.treatmentType() == TreatmentType.MODEL
                ? conditions.photoNotice().strip() : null;
    }

    /** 검증된 대표 이미지로 교체하거나 null로 삭제한다. */
    public void changeImage(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void changeStatus(TreatmentStatus status) {
        this.status = status;
    }
}
