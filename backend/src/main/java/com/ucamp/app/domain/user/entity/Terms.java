package com.ucamp.app.domain.user.entity;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.converter.BooleanToYnConverter;
import com.ucamp.app.common.entity.BaseTimeEntity;

/**
 * 약관 마스터. 코드·버전 단위로 한 행이며, 개정하면 같은 코드의 새 버전 행을 추가한다.
 * 약관 본문은 프론트에서 관리하고 여기에는 제목과 필수 여부만 둔다. 화면에서 등록·수정하지 않는다.
 */
@Entity
@Table(name = "TERMS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_terms")
    @SequenceGenerator(name = "seq_terms", sequenceName = "SEQ_TERMS", allocationSize = 1)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "TERMS_ID", nullable = false)
    private Long termsId;

    @Enumerated(EnumType.STRING)
    @Column(name = "CODE", nullable = false, length = 30)
    private TermsCode code;

    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @Column(name = "TITLE", nullable = false, length = 100)
    private String title;

    @Convert(converter = BooleanToYnConverter.class)
    @Column(name = "IS_REQUIRED", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private boolean required;
}
