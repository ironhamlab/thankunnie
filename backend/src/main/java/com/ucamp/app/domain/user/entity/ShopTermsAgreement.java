package com.ucamp.app.domain.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 매장의 약관 동의. 매장·약관 버전당 한 행이다 (UK_SHOP_TERMS_AGREEMENT).
 * 매장 계정은 APP_USER 행이 없어 회원 약관 동의(UserTermsAgreement)와 테이블을 나눈다.
 * WITHDRAWN_AT 은 철회 기능을 위한 자리로, MVP 에서는 철회가 없어 항상 null 이다.
 */
@Entity
@Table(name = "SHOP_TERMS_AGREEMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopTermsAgreement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_shop_terms_agreement")
    @SequenceGenerator(name = "seq_shop_terms_agreement", sequenceName = "SEQ_SHOP_TERMS_AGREEMENT",
            allocationSize = 1)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "AGREEMENT_ID", nullable = false)
    private Long agreementId;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SHOP_ID", nullable = false)
    private Long shopId;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "TERMS_ID", nullable = false)
    private Long termsId;

    @Column(name = "AGREED_AT", nullable = false)
    private LocalDateTime agreedAt;

    @Column(name = "WITHDRAWN_AT")
    private LocalDateTime withdrawnAt;

    @Builder
    private ShopTermsAgreement(Long shopId, Long termsId, LocalDateTime agreedAt) {
        this.shopId = shopId;
        this.termsId = termsId;
        this.agreedAt = agreedAt;
    }
}
