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
 * 선택 약관(문자 수신 등)은 철회할 수 있다. 철회하면 WITHDRAWN_AT 을 채우고, 다시 동의하면 같은 행을 되살린다.
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

    /** 동의를 철회한다. 이미 철회했으면 처음 철회한 시각을 그대로 둔다. */
    public void withdraw(LocalDateTime now) {
        if (withdrawnAt == null) {
            withdrawnAt = now;
        }
    }

    /** 철회한 동의에 다시 동의한다. 회원·약관 버전당 한 행(UK)이라 새 행 대신 이 행의 동의 시각을 바꾼다. */
    public void reagree(LocalDateTime now) {
        if (withdrawnAt != null) {
            agreedAt = now;
            withdrawnAt = null;
        }
    }
}
