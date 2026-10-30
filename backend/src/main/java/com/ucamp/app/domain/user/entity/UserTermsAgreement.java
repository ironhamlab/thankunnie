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
 * 회원의 약관 동의. 회원·약관 버전당 한 행이다 (UK_USER_TERMS_AGREEMENT).
 * WITHDRAWN_AT 은 철회 기능을 위한 자리로, MVP 에서는 철회가 없어 항상 null 이다.
 */
@Entity
@Table(name = "USER_TERMS_AGREEMENT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTermsAgreement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_user_terms_agreement")
    @SequenceGenerator(name = "seq_user_terms_agreement", sequenceName = "SEQ_USER_TERMS_AGREEMENT",
            allocationSize = 1)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "AGREEMENT_ID", nullable = false)
    private Long agreementId;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "TERMS_ID", nullable = false)
    private Long termsId;

    @Column(name = "AGREED_AT", nullable = false)
    private LocalDateTime agreedAt;

    @Column(name = "WITHDRAWN_AT")
    private LocalDateTime withdrawnAt;

    @Builder
    private UserTermsAgreement(Long userId, Long termsId, LocalDateTime agreedAt) {
        this.userId = userId;
        this.termsId = termsId;
        this.agreedAt = agreedAt;
    }

    /** 철회되지 않은 동의인지. */
    public boolean isActive() {
        return withdrawnAt == null;
    }
}
